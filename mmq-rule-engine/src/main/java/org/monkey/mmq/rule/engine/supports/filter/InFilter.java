/*
 * Copyright 2021-2021 Monkey Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.monkey.mmq.rule.engine.supports.filter;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.statement.select.SubSelect;
import org.monkey.mmq.rule.engine.ReactorQLMetadata;
import org.monkey.mmq.rule.engine.ReactorQLRecord;
import org.monkey.mmq.rule.engine.feature.FeatureId;
import org.monkey.mmq.rule.engine.feature.FilterFeature;
import org.monkey.mmq.rule.engine.feature.ValueMapFeature;
import org.monkey.mmq.rule.engine.utils.CompareUtils;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InFilter implements FilterFeature {

    @Override
    public BiFunction<ReactorQLRecord, Object, Mono<Boolean>> createPredicate(Expression expression, ReactorQLMetadata metadata) {

        InExpression inExpression = ((InExpression) expression);

        Expression left = inExpression.getLeftExpression();

        ItemsList in = (inExpression.getRightItemsList());

        List<Function<ReactorQLRecord, Publisher<?>>> rightMappers = new ArrayList<>();

        if (in instanceof ExpressionList) {
            rightMappers.addAll(((ExpressionList) in).getExpressions().stream()
                    .map(exp -> ValueMapFeature.createMapperNow(exp, metadata))
                    .collect(Collectors.toList()));
        }
        if (in instanceof SubSelect) {
            rightMappers.add(ValueMapFeature.createMapperNow(((SubSelect) in), metadata));
        }

        Function<ReactorQLRecord, Publisher<?>> leftMapper = ValueMapFeature.createMapperNow(left, metadata);

        boolean not = inExpression.isNot();
        return (ctx, column) ->
                doPredicate(not,
                        asFlux(leftMapper.apply(ctx)),
                        asFlux(Flux.fromIterable(rightMappers).flatMap(mapper -> mapper.apply(ctx)))
                );
    }

    protected Flux<Object> asFlux(Publisher<?> publisher) {
        return Flux.from(publisher)
                .flatMap(v -> {
                    if (v instanceof Iterable) {
                        return Flux.fromIterable(((Iterable<?>) v));
                    }
                    if (v instanceof Publisher) {
                        return ((Publisher<?>) v);
                    }
                    if (v instanceof Map && ((Map<?, ?>) v).size() == 1) {
                        return Mono.just(((Map<?, ?>) v).values().iterator().next());
                    }
                    return Mono.just(v);
                });
    }

    protected Mono<Boolean> doPredicate(boolean not, Flux<Object> left, Flux<Object> values) {
        return values
                .flatMap(v -> left.map(l -> CompareUtils.equals(v, l)))
                .any(Boolean.TRUE::equals)
                .map(v -> not != v);
    }

    @Override
    public String getId() {
        return FeatureId.Filter.in.getId();
    }
}
