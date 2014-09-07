/*
 * Copyright 2011-2014 the original author or authors.
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
package org.glowroot.plugin.jdbc;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.api.Message;
import org.glowroot.api.MessageSupplier;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
class JdbcMessageSupplier extends MessageSupplier {

    private static final int NEXT_HAS_NOT_BEEN_CALLED = -1;

    @Nullable
    private final String sql;

    // parameters and batchedParameters cannot both be non-null

    // cannot use ImmutableList for parameters since it can contain null elements
    @Nullable
    private final List</*@Nullable*/Object> parameters;
    @Nullable
    private final ImmutableList<List</*@Nullable*/Object>> batchedParameters;

    // this is only used for batching of non-PreparedStatements
    @Nullable
    private final ImmutableList<String> batchedSqls;

    // intentionally not volatile for performance, but it does mean partial and active trace
    // captures may see stale value (but partial and active trace captures use memory barrier in
    // Trace to ensure the values are at least visible as of the end of the last trace entry)
    private int numRows = NEXT_HAS_NOT_BEEN_CALLED;

    static JdbcMessageSupplier create(String sql) {
        return new JdbcMessageSupplier(sql, null, null, null);
    }

    static JdbcMessageSupplier createWithParameters(PreparedStatementMirror mirror) {
        return new JdbcMessageSupplier(mirror.getSql(), mirror.getParametersCopy(), null, null);
    }

    static JdbcMessageSupplier createWithBatchedSqls(StatementMirror mirror) {
        return new JdbcMessageSupplier(null, null, null, mirror.getBatchedSqlCopy());
    }

    static JdbcMessageSupplier createWithBatchedParameters(PreparedStatementMirror mirror) {
        return new JdbcMessageSupplier(mirror.getSql(), null, mirror.getBatchedParametersCopy(),
                null);
    }

    private JdbcMessageSupplier(@Nullable String sql,
            @Nullable List</*@Nullable*/Object> parameters,
            @Nullable ImmutableList<List</*@Nullable*/Object>> batchedParameters,
            @Nullable ImmutableList<String> batchedSqls) {
        if (sql == null && batchedSqls == null) {
            throw new AssertionError("Constructor args 'sql' and 'batchedSqls' cannot both"
                    + " be null (enforced by static factory methods)");
        }
        this.sql = sql;
        this.parameters = parameters;
        this.batchedParameters = batchedParameters;
        this.batchedSqls = batchedSqls;
    }

    @Override
    public Message get() {
        StringBuilder sb = new StringBuilder();
        sb.append("jdbc execution: ");
        if (batchedSqls != null) {
            appendBatchedSqls(sb, batchedSqls);
            return Message.from(sb.toString());
        }
        if (sql == null) {
            throw new AssertionError("Fields 'sql' and 'batchedSqls' cannot both be null"
                    + " (enforced by static factory methods)");
        }
        if (isUsingBatchedParameters() && batchedParameters.size() > 1) {
            // print out number of batches to make it easy to identify
            sb.append(Integer.toString(batchedParameters.size()));
            sb.append(" x ");
        }
        int numArgs = (numRows == NEXT_HAS_NOT_BEEN_CALLED) ? 1 : 2;
        String[] args = new String[numArgs];
        sb.append("{}");
        args[0] = sql;
        appendParameters(sb);
        appendRowCount(sb, args);
        return Message.from(sb.toString(), args);
    }

    void setHasPerformedNavigation() {
        if (numRows == NEXT_HAS_NOT_BEEN_CALLED) {
            numRows = 0;
        }
    }

    void updateNumRows(int currentRow) {
        this.numRows = Math.max(this.numRows, currentRow);
    }

    @EnsuresNonNullIf(expression = "parameters", result = true)
    private boolean isUsingParameters() {
        return parameters != null;
    }

    @EnsuresNonNullIf(expression = "batchedParameters", result = true)
    private boolean isUsingBatchedParameters() {
        return batchedParameters != null;
    }

    private void appendParameters(StringBuilder sb) {
        if (isUsingParameters() && !parameters.isEmpty()) {
            appendParameters(sb, parameters);
        } else if (isUsingBatchedParameters()) {
            for (List</*@Nullable*/Object> oneParameters : batchedParameters) {
                appendParameters(sb, oneParameters);
            }
        }
    }

    private void appendRowCount(StringBuilder sb, String[] args) {
        if (numRows == NEXT_HAS_NOT_BEEN_CALLED) {
            return;
        }
        sb.append(" => {}");
        if (numRows == 1) {
            sb.append(" row");
        } else {
            sb.append(" rows");
        }
        args[1] = Integer.toString(numRows);
    }

    private static void appendBatchedSqls(StringBuilder sb, ImmutableList<String> batchedSqls) {
        boolean first = true;
        for (String batchedSql : batchedSqls) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(batchedSql);
            first = false;
        }
    }

    private static void appendParameters(StringBuilder sb, List</*@Nullable*/Object> parameters) {
        sb.append(" [");
        boolean first = true;
        for (Object parameter : parameters) {
            if (!first) {
                sb.append(", ");
            }
            if (parameter instanceof String) {
                sb.append("\'");
                sb.append((String) parameter);
                sb.append("\'");
            } else {
                sb.append(String.valueOf(parameter));
            }
            first = false;
        }
        sb.append("]");
    }
}
