/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.asterix.lang.sqlpp.util;

import org.apache.asterix.common.exceptions.AsterixException;
import org.apache.asterix.lang.common.base.Expression;
import org.apache.asterix.lang.common.base.Expression.Kind;
import org.apache.asterix.lang.common.expression.FieldAccessor;
import org.apache.asterix.lang.common.expression.VariableExpr;
import org.apache.asterix.lang.common.struct.VarIdentifier;
import org.apache.asterix.lang.sqlpp.parser.ParseException;
import org.apache.log4j.Logger;

public class ExpressionToVariableUtil {

    private static final Logger LOGGER = Logger.getLogger(ExpressionToVariableUtil.class);

    private ExpressionToVariableUtil() {
    }

    private static String getGeneratedIdentifier(Expression expr) throws ParseException {
        if (expr.getKind() == Kind.VARIABLE_EXPRESSION) {
            VariableExpr bindingVarExpr = (VariableExpr) expr;
            return bindingVarExpr.getVar().getValue();
        } else if (expr.getKind() == Kind.FIELD_ACCESSOR_EXPRESSION) {
            FieldAccessor fa = (FieldAccessor) expr;
            return SqlppVariableUtil.toInternalVariableName(fa.getIdent().getValue());
        } else {
            try {
                throw new ParseException(
                        "Need an alias for the enclosed expression:\n" + SqlppFormatPrintUtil.toString(expr));
            } catch (AsterixException e) {
                LOGGER.error(e.getLocalizedMessage(), e);
                throw new ParseException(e.getLocalizedMessage());
            }
        }
    }

    /**
     * Generates an identifier according to an expression.
     *
     * @param expr
     *            the input expression.
     * @param raiseError,
     *            if it is not possible to generate an identifier from the input expression,
     *            to raise the error if true, and to return a null if false.
     * @return the generated identifier.
     * @throws ParseException
     */
    public static String getGeneratedIdentifier(Expression expr, boolean raiseError) throws ParseException {
        try {
            return getGeneratedIdentifier(expr);
        } catch (ParseException e) {
            if (raiseError) {
                throw e;
            }
            return null;
        }
    }

    /**
     * Generates a variable according to an expression.
     *
     * @param expr
     *            the input expression.
     * @param raiseError,
     *            if it is not possible to generate a variable from the input expression,
     *            to raise the error if true, and to return a null if false.
     * @return the generated variable.
     * @throws ParseException
     */
    public static VariableExpr getGeneratedVariable(Expression expr, boolean raiseError) throws ParseException {
        try {
            String varName = getGeneratedIdentifier(expr);
            VarIdentifier var = new VarIdentifier(varName);
            VariableExpr varExpr = new VariableExpr();
            varExpr.setVar(var);
            return varExpr;
        } catch (ParseException e) {
            if (raiseError) {
                throw e;
            }
            return null;
        }
    }

}
