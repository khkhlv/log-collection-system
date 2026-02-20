package ru.khkhlv.logcollector.repository;

import ru.khkhlv.logcollector.model.LogEntry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;

@Repository
@Transactional(readOnly = true)
public class LogRepositoryImpl implements LogRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<LogEntry> searchLogs(
            String messageContains,
            String level,
            String source,
            String host,
            String environment,
            Instant from,
            Instant to,
            Map<String, Object> payloadFilters,
            Pageable pageable
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // Основной запрос для получения данных
        CriteriaQuery<LogEntry> query = cb.createQuery(LogEntry.class);
        Root<LogEntry> root = query.from(LogEntry.class);

        // СОЗДАЕМ НОВЫЙ список предикатов для основного запроса
        List<Predicate> queryPredicates = buildPredicates(
                cb, root, messageContains, level, source, host, environment, from, to, payloadFilters
        );

        query.where(queryPredicates.toArray(new Predicate[0]))
                .orderBy(cb.desc(root.get("createdAt")));

        TypedQuery<LogEntry> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<LogEntry> results = typedQuery.getResultList();

        // Отдельный запрос для подсчета общего количества
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<LogEntry> countRoot = countQuery.from(LogEntry.class);

        // СОЗДАЕМ НОВЫЙ список предикатов для count запроса
        List<Predicate> countPredicates = buildPredicates(
                cb, countRoot, messageContains, level, source, host, environment, from, to, payloadFilters
        );

        countQuery.select(cb.count(countRoot))
                .where(countPredicates.toArray(new Predicate[0]));

        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public long countByCriteria(String level, String source, Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<LogEntry> root = query.from(LogEntry.class);

        List<Predicate> predicates = new ArrayList<>();

        if (StringUtils.hasText(level)) {
            predicates.add(cb.equal(cb.lower(root.get("level")), level.toLowerCase()));
        }
        if (StringUtils.hasText(source)) {
            predicates.add(cb.equal(root.get("source"), source));
        }
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        query.select(cb.count(root))
                .where(predicates.toArray(new Predicate[0]));

        return entityManager.createQuery(query).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            Root<LogEntry> root,
            String messageContains,
            String level,
            String source,
            String host,
            String environment,
            Instant from,
            Instant to,
            Map<String, Object> payloadFilters
    ) {
        List<Predicate> predicates = new ArrayList<>();

        if (StringUtils.hasText(messageContains)) {
            predicates.add(cb.like(cb.lower(root.get("message")),
                    "%" + messageContains.toLowerCase() + "%"));
        }
        if (StringUtils.hasText(level)) {
            predicates.add(cb.equal(cb.lower(root.get("level")), level.toLowerCase()));
        }
        if (StringUtils.hasText(source)) {
            predicates.add(cb.equal(root.get("source"), source));
        }
        if (StringUtils.hasText(host)) {
            predicates.add(cb.equal(root.get("host"), host));
        }
        if (StringUtils.hasText(environment)) {
            predicates.add(cb.equal(root.get("environment"), environment));
        }
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        if (payloadFilters != null && !payloadFilters.isEmpty()) {
            for (Map.Entry<String, Object> entry : payloadFilters.entrySet()) {
                predicates.add(cb.equal(
                        cb.function("jsonb_extract_path_text", String.class,
                                root.get("payload"), cb.literal(entry.getKey())),
                        entry.getValue().toString()
                ));
            }
        }

        return predicates;
    }
}