#!/usr/bin/env python3
"""
Скрипт инициализации Superset с созданием чартов
При каждом запуске удаляет существующие чарты и дашборды и создаёт заново
"""

import os
import sys
import json
import logging
from pathlib import Path

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

POSTGRES_CONFIG = {
    'host': os.getenv('POSTGRES_HOST', 'postgres'),
    'port': int(os.getenv('POSTGRES_PORT', 5432)),
    'database': os.getenv('POSTGRES_DB', 'logdb'),
    'user': os.getenv('POSTGRES_USER', 'loguser'),
    'password': os.getenv('POSTGRES_PASSWORD', 'logpass')
}

DATASETS_DIR = '/app/pythonpath/datasets'


def wait_for_postgres():
    logger.info("Waiting for PostgreSQL...")
    import psycopg2
    from psycopg2 import OperationalError

    for attempt in range(30):
        try:
            conn = psycopg2.connect(
                host=POSTGRES_CONFIG['host'],
                port=POSTGRES_CONFIG['port'],
                dbname=POSTGRES_CONFIG['database'],
                user=POSTGRES_CONFIG['user'],
                password=POSTGRES_CONFIG['password'],
                connect_timeout=5
            )
            conn.close()
            logger.info("✅ PostgreSQL is ready!")
            return True
        except OperationalError as e:
            logger.info(f"Attempt {attempt + 1}/30: {e}")
            import time
            time.sleep(2)

    logger.error("❌ PostgreSQL not ready")
    return False


def cleanup_existing(db_session):
    """Удаление существующих чартов и дашбордов"""
    logger.info("🧹 Cleaning up existing charts and dashboards...")

    from superset.models.dashboard import Dashboard
    from superset.models.slice import Slice

    # Удаляем все дашборды
    dashboards = db_session.query(Dashboard).all()
    for dashboard in dashboards:
        logger.info(f"🗑️ Deleting dashboard: {dashboard.dashboard_title}")
        db_session.delete(dashboard)

    # Удаляем все чарты
    charts = db_session.query(Slice).all()
    for chart in charts:
        logger.info(f"🗑️ Deleting chart: {chart.slice_name}")
        db_session.delete(chart)

    db_session.commit()
    logger.info(f"✅ Cleanup complete: removed {len(charts)} charts and {len(dashboards)} dashboards")


def add_virtual_columns(db_session, dataset_id):
    """Добавление виртуальных вычисляемых колонок в датасет"""
    from superset.connectors.sqla.models import SqlaTable, TableColumn

    dataset = db_session.query(SqlaTable).filter_by(id=dataset_id).first()
    if not dataset:
        logger.error(f"Dataset {dataset_id} not found")
        return False

    logger.info("➕ Adding virtual columns to dataset...")

    # Удаляем существующие виртуальные колонки
    db_session.query(TableColumn).filter_by(table_id=dataset_id).delete()
    db_session.commit()

    # Словарь виртуальных колонок: имя -> SQL выражение
    virtual_columns = {
        "error_type": "payload->>'error_type'",
        "user_id": "payload->>'user_id'",
        "duration_ms": "(payload->>'duration_ms')::int",
        "http_status_code": "(payload->>'http_status_code')::int"
    }

    for col_name, sql_expr in virtual_columns.items():
        # Создаём виртуальную колонку
        col = TableColumn(
            table_id=dataset_id,
            column_name=col_name,
            expression=sql_expr,
            is_dttm=False,
            is_active=True,
            type="STRING",
            description=f"Extracted from payload JSON: {sql_expr}"
        )
        db_session.add(col)
        logger.info(f"✅ Added virtual column: {col_name} = {sql_expr}")

    db_session.commit()

    # Обновляем метаданные датасета
    dataset.fetch_metadata()
    db_session.commit()

    logger.info("✅ Virtual columns added successfully")
    return True


def create_chart(db_session, dataset_id, chart_name, viz_type, form_data):
    """Создание чарта через ORM (Superset 4.0)"""
    from superset.models.slice import Slice

    try:
        chart = Slice(
            slice_name=chart_name,
            viz_type=viz_type,
            datasource_type="table",
            datasource_id=dataset_id,
            params=json.dumps(form_data),
            query_context=json.dumps({}),
            description=f"Auto-generated: {chart_name}",
            owners=[]
        )
        db_session.add(chart)
        db_session.commit()
        logger.info(f"✅ Chart '{chart_name}' created (ID: {chart.id})")
        return chart.id
    except Exception as e:
        logger.error(f"❌ Chart '{chart_name}': {e}")
        db_session.rollback()
        import traceback
        traceback.print_exc()
        return None


def create_charts(db_session, dataset_id):
    """Создание всех чартов для дашборда"""
    logger.info("Creating charts...")

    chart_ids = {}

    # 1. Общее количество логов по времени (Line Chart)
    chart_id = create_chart(
        db_session, dataset_id, "Logs Over Time", "line",
        {
            "datasource": f"{dataset_id}__table",
            "viz_type": "line",
            "time_range": "No filter",
            "granularity_sqla": "created_at",
            "time_grain_sqla": "PT1H",
            "metrics": [
                {
                    "expressionType": "SIMPLE",
                    "column": {"column_name": "id"},
                    "aggregate": "COUNT",
                    "label": "COUNT(id)"
                }
            ],
            "groupby": [],
            "row_limit": 10000,
            "order_desc": True
        }
    )
    if chart_id:
        chart_ids["logs_over_time"] = chart_id

    # 2. Распределение логов по уровням (Bar Chart)
    chart_id = create_chart(
        db_session, dataset_id, "Logs by Level", "dist_bar",
        {
            "datasource": f"{dataset_id}__table",
            "viz_type": "dist_bar",
            "groupby": ["level"],
            "metrics": [
                {
                    "expressionType": "SIMPLE",
                    "column": {"column_name": "id"},
                    "aggregate": "COUNT"
                }
            ],
            "row_limit": 10,
            "order_desc": True,
            "columns": []
        }
    )
    if chart_id:
        chart_ids["logs_by_level"] = chart_id

    # 3. Топ-10 источников логов (Bar Chart)
    chart_id = create_chart(
        db_session, dataset_id, "Top 10 Sources", "dist_bar",
        {
            "datasource": f"{dataset_id}__table",
            "viz_type": "dist_bar",
            "groupby": ["source"],
            "metrics": [
                {
                    "expressionType": "SIMPLE",
                    "column": {"column_name": "id"},
                    "aggregate": "COUNT"
                }
            ],
            "row_limit": 10,
            "order_desc": True,
            "columns": [],
            "order_by_cols": ["-COUNT(id)"]
        }
    )
    if chart_id:
        chart_ids["top_sources"] = chart_id

    # 4. Топ-3 типов ошибок (используем виртуальную колонку error_type)
    chart_id = create_chart(
        db_session, dataset_id, "Top 3 Error Types", "dist_bar",
        {
            "datasource": f"{dataset_id}__table",
            "viz_type": "dist_bar",
            "groupby": ["error_type"],
            "metrics": [
                {
                    "expressionType": "SIMPLE",
                    "column": {"column_name": "id"},
                    "aggregate": "COUNT",
                    "label": "Error Count"
                }
            ],
            "row_limit": 3,
            "order_desc": True,
            "adhoc_filters": [
                {
                    "clause": "WHERE",
                    "expressionType": "SIMPLE",
                    "operator": "==",
                    "subject": "level",
                    "comparator": "ERROR"
                },
                {
                    "clause": "WHERE",
                    "expressionType": "SIMPLE",
                    "operator": "IS NOT NULL",
                    "subject": "error_type"
                }
            ]
        }
    )
    if chart_id:
        chart_ids["top_error_types"] = chart_id

    # 5. Ошибки по источникам (Bar Chart)
    chart_id = create_chart(
        db_session, dataset_id, "Errors by Source", "dist_bar",
        {
            "datasource": f"{dataset_id}__table",
            "viz_type": "dist_bar",
            "groupby": ["source", "error_type"],
            "metrics": [
                {
                    "expressionType": "SIMPLE",
                    "column": {"column_name": "id"},
                    "aggregate": "COUNT"
                }
            ],
            "row_limit": 10,
            "order_desc": True,
            "stacked": True,
            "adhoc_filters": [
                {
                    "clause": "WHERE",
                    "expressionType": "SIMPLE",
                    "operator": "==",
                    "subject": "level",
                    "comparator": "ERROR"
                },
                {
                    "clause": "WHERE",
                    "expressionType": "SIMPLE",
                    "operator": "IS NOT NULL",
                    "subject": "error_type"
                }
            ]
        }
    )
    if chart_id:
        chart_ids["errors_by_source"] = chart_id

    # 6. Аномальный рост ошибок
    chart_id = create_chart(
        db_session, dataset_id, "Error Rate Anomaly", "line",
        {
            "datasource": f"{dataset_id}__table",
            "viz_type": "line",
            "time_range": "Last 7 days",
            "granularity_sqla": "created_at",
            "time_grain_sqla": "PT1H",
            "metrics": [
                {
                    "expressionType": "SQL",
                    "sqlExpression": "SUM(CASE WHEN level = 'ERROR' THEN 1 ELSE 0 END) * 100.0 / COUNT(*)",
                    "label": "Error Rate %"
                }
            ],
            "groupby": [],
            "row_limit": 10000
        }
    )
    if chart_id:
        chart_ids["error_rate_anomaly"] = chart_id

    # 7. Dead Services
    chart_id = create_chart(
        db_session, dataset_id, "Dead Services", "table",
        {
            "datasource": f"{dataset_id}__table",
            "viz_type": "table",
            "groupby": ["source"],
            "metrics": [
                {
                    "expressionType": "SQL",
                    "sqlExpression": "MAX(created_at)",
                    "label": "last_log_time"
                },
                {
                    "expressionType": "SQL",
                    "sqlExpression": "EXTRACT(EPOCH FROM (NOW() - MAX(created_at))) / 3600",
                    "label": "hours_since_last_log"
                },
                {
                    "expressionType": "SQL",
                    "sqlExpression": "CASE WHEN MAX(created_at) < NOW() - INTERVAL '1 hour' THEN 'DEAD' ELSE 'ACTIVE' END",
                    "label": "status"
                }
            ],
            "row_limit": 50,
            "order_desc": False,
            "order_by_cols": ["hours_since_last_log DESC"]
        }
    )
    if chart_id:
        chart_ids["dead_services"] = chart_id

    logger.info(f"✅ Created {len(chart_ids)} charts")
    return chart_ids


def create_dashboard(db_session, chart_ids):
    """Создание дашборда с чартами"""
    logger.info("Creating dashboard...")

    from superset.models.dashboard import Dashboard

    position_json = {
        "GRID": {
            "children": ["CHART-1", "CHART-2", "CHART-3", "CHART-4", "CHART-5", "CHART-6", "CHART-7"],
            "id": "GRID",
            "meta": {"width": 12, "height": 300},
            "type": "GRID"
        }
    }

    chart_positions = [
        ("CHART-1", "logs_over_time", 0, 12, 50),
        ("CHART-2", "logs_by_level", 1, 6, 50),
        ("CHART-3", "top_sources", 1, 6, 50),
        ("CHART-4", "top_error_types", 2, 4, 50),
        ("CHART-5", "errors_by_source", 2, 8, 50),
        ("CHART-6", "error_rate_anomaly", 3, 12, 50),
        ("CHART-7", "dead_services", 4, 12, 50)
    ]

    for chart_id, chart_key, row, width, height in chart_positions:
        if chart_key in chart_ids:
            position_json[chart_id] = {
                "children": [],
                "id": chart_id,
                "meta": {
                    "chartId": chart_ids[chart_key],
                    "width": width,
                    "height": height,
                    "row": row,
                    "column": 0
                },
                "type": "CHART"
            }

    dashboard = Dashboard(
        dashboard_title="Log Collection System",
        description="Analytics dashboard for log collection system monitoring",
        position_json=json.dumps(position_json),
        json_metadata=json.dumps({
            "refresh_frequency": 30,
            "timed_refresh_immune_slices": [],
            "expanded_slices": {},
            "default_filters": {}
        }),
        published=True,
        owners=[]
    )

    from superset.models.slice import Slice
    for chart_id in chart_ids.values():
        chart = db_session.query(Slice).filter_by(id=chart_id).first()
        if chart:
            dashboard.slices.append(chart)

    db_session.add(dashboard)
    db_session.commit()

    logger.info(f"✅ Dashboard created with ID: {dashboard.id}")
    return dashboard.id


def init_superset():
    logger.info("Initializing Superset...")

    from superset import db
    from superset.connectors.sqla.models import Database, SqlaTable

    database_id = None
    dataset_id = None

    # 1. Подключение к БД
    logger.info("Step 1: Creating database connection...")
    database = db.session.query(Database).filter_by(database_name="Logs DB").first()

    if not database:
        logger.info("➕ Creating database connection: Logs DB")

        extra = {
            "allow_csv_upload": True,
            "allow_ctas": True,
            "allow_cvas": True,
            "allow_dml": True,
            "allow_run_async": True,
            "metadata_params": {},
            "engine_params": {},
            "cost_estimate_enabled": False,
            "schemas_allowed_for_csv_upload": []
        }

        database = Database(
            database_name="Logs DB",
            sqlalchemy_uri=f"postgresql+psycopg2://{POSTGRES_CONFIG['user']}:{POSTGRES_CONFIG['password']}@{POSTGRES_CONFIG['host']}:{POSTGRES_CONFIG['port']}/{POSTGRES_CONFIG['database']}",
            expose_in_sqllab=True,
            cache_timeout=300,
            extra=json.dumps(extra)
        )
        db.session.add(database)
        db.session.commit()
        logger.info("✅ Database connection created")
    else:
        logger.info("✅ Database connection already exists")

    database_id = database.id
    logger.info(f"📊 Database ID: {database_id}")

    # 1.5 Очистка существующих чартов и дашбордов
    cleanup_existing(db.session)

    # 2. Датасеты
    logger.info("Step 2: Creating datasets...")

    # Удаляем существующий датасет если есть
    existing_dataset = db.session.query(SqlaTable).filter_by(
        table_name="logs",
        database_id=database_id
    ).first()

    if existing_dataset:
        logger.info(f"🗑️ Deleting existing dataset 'logs' (ID: {existing_dataset.id})")
        db.session.delete(existing_dataset)
        db.session.commit()

    # Создаем новый датасет
    logger.info("📝 Creating dataset 'logs'...")
    dataset = SqlaTable(
        table_name="logs",
        database_id=database_id,
        schema="public",
        is_sqllab_view=False,
        main_dttm_col="created_at",
        description="Raw logs table",
        owners=[]
    )
    db.session.add(dataset)
    db.session.commit()

    # Загружаем метаданные колонок
    dataset.fetch_metadata()
    db.session.commit()

    dataset_id = dataset.id
    logger.info(f"✅ Dataset 'logs' created (ID: {dataset_id})")

    # 2.1 Добавляем виртуальные колонки
    add_virtual_columns(db.session, dataset_id)

    # 3. Создаём чарты
    logger.info("Step 3: Creating charts...")
    chart_ids = create_charts(db.session, dataset_id)

    # 4. Создаём дашборд
    logger.info("Step 4: Creating dashboard...")
    if chart_ids:
        create_dashboard(db.session, chart_ids)
    else:
        logger.warning("⚠️ No charts created, skipping dashboard")

    logger.info("✅ Superset initialization completed!")
    return database_id, dataset_id, chart_ids


def main():
    logger.info("=" * 50)
    logger.info("Starting Superset initialization")
    logger.info("=" * 50)

    if not wait_for_postgres():
        sys.exit(1)

    os.environ['SUPERSET_CONFIG_PATH'] = '/app/pythonpath/superset_config.py'

    from superset.app import create_app
    app = create_app()

    with app.app_context():
        database_id, dataset_id, chart_ids = init_superset()

    logger.info("=" * 50)
    logger.info("✅ Superset initialization completed!")
    logger.info("=" * 50)
    logger.info("Login: admin / admin")
    logger.info(f"Database ID: {database_id}")
    logger.info(f"Dataset ID: {dataset_id}")
    logger.info(f"Charts created: {len(chart_ids)}")
    logger.info("=" * 50)


if __name__ == "__main__":
    main()