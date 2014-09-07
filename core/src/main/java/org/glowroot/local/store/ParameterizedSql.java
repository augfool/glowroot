/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.local.store;

import java.util.List;

import com.google.common.collect.ImmutableList;

import org.glowroot.markers.Immutable;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@Immutable
class ParameterizedSql {

    private final String sql;
    private final ImmutableList<Object> args;

    ParameterizedSql(String sql, List<Object> args) {
        this.sql = sql;
        this.args = ImmutableList.copyOf(args);
    }

    String getSql() {
        return sql;
    }

    Object[] getArgs() {
        return args.toArray(new Object[args.size()]);
    }
}
