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

package org.monkey.mmq.rule.engine.supports.group;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import org.apache.commons.collections.CollectionUtils;
import org.monkey.mmq.rule.engine.ReactorQLMetadata;
import org.monkey.mmq.rule.engine.ReactorQLRecord;
import org.monkey.mmq.rule.engine.feature.FeatureId;
import org.monkey.mmq.rule.engine.feature.GroupFeature;
import org.monkey.mmq.rule.engine.utils.CastUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * 窗口函数
 * <pre>
 * group by _window(10) => flux.window(10)
 * <p>
 * group by _window('1s') => flux.window(Duration.ofSeconds(1))
 * </pre>
 *
 * @author solley
 * @since 1.0
 */
public class GroupByWindowFeature implements GroupFeature {

    private static final String ID = FeatureId.GroupBy.of("_window").getId();

    @Override
    public Function<Flux<ReactorQLRecord>, Flux<Flux<ReactorQLRecord>>> createGroupMapper(Expression expression, ReactorQLMetadata metadata) {

        net.sf.jsqlparser.expression.Function windowFunc = ((net.sf.jsqlparser.expression.Function) expression);

        ExpressionList parameters = windowFunc.getParameters();
        List<Expression> expressions;
        if (parameters == null || CollectionUtils.isEmpty(expressions = parameters.getExpressions())) {
            throw new UnsupportedOperationException("窗口函数必须传入参数,如: window('10s') , window(30)");
        }
        try {
            if (expressions.size() == 1) {
                return createOneParameter(expressions, metadata);
            } else if (expressions.size() == 2) {
                return createTwoParameter(expressions, metadata);
            }
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedOperationException("不支持的函数[ " + expression + " ] : " + e.getMessage(), e);
        }
        throw new UnsupportedOperationException("函数[ " + expression + " ]参数数量错误,最小1,最大2.");
    }

    protected Function<Flux<ReactorQLRecord>, Flux<Flux<ReactorQLRecord>>> createOneParameter(List<Expression> expressions, ReactorQLMetadata metadata) {
        Expression expr = expressions.get(0);
        // _window(100)
        if (expr instanceof LongValue) {
            int val = (int) ((LongValue) expr).getValue();
            if (val <= 0) {
                throw new UnsupportedOperationException("窗口数量不能小于0:" + expr);
            }
            return flux -> flux.window((val));
        }
        // _window('1s')
        if (expr instanceof StringValue) {
            Duration duration = CastUtils.parseDuration(((StringValue) expr).getValue());
            if (duration.toMillis() <= 0) {
                throw new UnsupportedOperationException("窗口时间不能小于0:" + expr);
            }
            return flux -> flux.window(duration);
        }
        throw new UnsupportedOperationException("不支持的窗口表达式:" + expr);
    }

    protected Function<Flux<ReactorQLRecord>, Flux<Flux<ReactorQLRecord>>> createTwoParameter(List<Expression> expressions, ReactorQLMetadata metadata) {
        Expression first = expressions.get(0);
        Expression second = expressions.get(1);

        // window(100,10)
        if (first instanceof LongValue && second instanceof LongValue) {
            int val = (int) ((LongValue) first).getValue();
            int secondVal = (int) ((LongValue) second).getValue();
            if (val <= 0 || secondVal <= 0) {
                throw new UnsupportedOperationException("窗口时间不能小于0: " + (val <= 0 ? first : second));
            }
            return flux -> flux.window(val, secondVal);
        }
        // window('1s','10s')
        if (first instanceof StringValue && second instanceof StringValue) {
            Duration windowingTimespan = CastUtils.parseDuration(((StringValue) first).getValue());
            Duration openWindowEvery = CastUtils.parseDuration(((StringValue) second).getValue());
            if (windowingTimespan.toMillis() <= 0 || openWindowEvery.toMillis() <= 0) {
                throw new UnsupportedOperationException("窗口时间不能小于0: " + (windowingTimespan.toMillis() <= 0 ? first : second));
            }
            return flux -> flux.window(windowingTimespan, openWindowEvery);
        }
        //windowTimeout(100,'20s')
        if (first instanceof LongValue && second instanceof StringValue) {
            int max = (int) ((LongValue) first).getValue();
            Duration timeout = CastUtils.parseDuration(((StringValue) second).getValue());
            if (timeout.toMillis() <= 0) {
                throw new UnsupportedOperationException("窗口时间不能小于0: " + second);
            }
            if (max <= 0) {
                throw new UnsupportedOperationException("窗口时间不能小于0: " + first);
            }
            return flux -> flux.windowTimeout(max, timeout);
        }
        throw new UnsupportedOperationException("不支持的参数: " + first + " , " + second);
    }

    @Override
    public String getId() {
        return ID;
    }
}
