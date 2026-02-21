# superset/superset_config.py
import os

# Секретный ключ
SECRET_KEY = os.getenv('SUPERSET_SECRET_KEY', 'dev-secret-key')

# Подключение к метабазе Superset
SQLALCHEMY_DATABASE_URI = os.getenv(
    'SQLALCHEMY_DATABASE_URI',
    'postgresql+psycopg2://loguser:logpass@postgres:5432/superset_meta'
)

# Отключаем примеры
SUPERSET_LOAD_EXAMPLES = False

# Разрешаем CSV upload
ALLOW_CSV_UPLOAD = True

# Логирование
LOG_LEVEL = 'INFO'

# CORS (для разработки)
ENABLE_CORS = True
CORS_OPTIONS = {
    'supports_credentials': True,
    'allow_headers': ['*'],
    'resources': ['*'],
    'origins': ['*']
}

# CSRF (отключаем для разработки)
WTF_CSRF_ENABLED = False
TALISMAN_ENABLED = False