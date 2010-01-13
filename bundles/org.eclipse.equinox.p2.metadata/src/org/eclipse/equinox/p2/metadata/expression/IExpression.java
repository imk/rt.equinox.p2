/*******************************************************************************
 * Copyright (c) 2009 - 2010 Cloudsmith Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Cloudsmith Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.metadata.expression;

/**
 * A node in the expression tree
 * @since 2.0
 */
public interface IExpression {
	int TYPE_ALL = 1;
	int TYPE_AND = 2;
	int TYPE_AT = 3;
	int TYPE_EQUALS = 4;
	int TYPE_EXISTS = 5;
	int TYPE_GREATER = 6;
	int TYPE_GREATER_EQUAL = 7;
	int TYPE_LAMBDA = 8;
	int TYPE_LESS = 9;
	int TYPE_LESS_EQUAL = 10;
	int TYPE_LITERAL = 11;
	int TYPE_MATCHES = 12;
	int TYPE_MEMBER = 13;
	int TYPE_NOT = 14;
	int TYPE_NOT_EQUALS = 15;
	int TYPE_OR = 16;
	int TYPE_PARAMETER = 17;
	int TYPE_VARIABLE = 18;

	/**
	 * Let the visitor visit this instance and all expressions that this
	 * instance contains.
	 * @param visitor The visiting visitor.
	 * @return <code>true</code> if the visitor should continue visiting, <code>false</code> otherwise.
	 */
	boolean accept(IExpressionVisitor visitor);

	/**
	 * Evaluate this expression with given context and variables.
	 * @param context The evaluation context
	 * @return The result of the evaluation.
	 */
	Object evaluate(IEvaluationContext context);

	/**
	 * Returns the expression type (see TYPE_xxx constants).
	 */
	int getExpressionType();

	/**
	 * Appends the string representation of this expression to the collector <code>collector</code>.
	 */
	void toString(StringBuffer collector);

	/**
	 * Appends the an LDAP filter representation of this expression to the <code>collector</code>.
	 * @throws UnsupportedOperationException if the expression contains nodes
	 * that cannot be represented in an LDAP filter
	 */
	void toLDAPString(StringBuffer collector);
}