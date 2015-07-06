package org.semanticweb.ontop.pivotalrepr;


import org.semanticweb.ontop.model.OrderCondition;

import java.util.List;

/**
 * Query modifiers (DISTINCT, COUNT, OFFSET, etc.)
 *
 * Allows immutable implementations.
 */
public interface QueryModifiers {

    boolean isDistinct();
    boolean isCount();
    boolean hasOrder();
    boolean hasLimit();
    boolean hasOffset();
    long getLimit();
    long getOffset();
    List<OrderCondition> getSortConditions();
}