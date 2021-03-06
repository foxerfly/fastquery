/*
 * Copyright (c) 2016-2088, fastquery.org and/or its affiliates. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * For more information, please see http://www.fastquery.org/.
 * 
 */

package org.fastquery.filter.generate.querya;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.fastquery.core.Param;
import org.fastquery.core.Placeholder;
import org.fastquery.core.Query;
import org.fastquery.filter.generate.common.MethodFilter;
import org.fastquery.util.TypeUtil;
import org.fastquery.where.Condition;

/**
 * 如果Query,Condition中出现有":name" 表达式, 那么当前方法必须存在Parm注解并且保持一致. <br>
 * Parm("") 命名时禁止用""字符串
 * 
 * @author xixifeng (fastquery@126.com)
 */
public class MarkFilter implements MethodFilter {

	@Override
	public Method doFilter(Method method) {

		Set<String> params = new HashSet<>();
		Parameter[] parameters = method.getParameters();
		for (Parameter parameter : parameters) {
			Param param = parameter.getAnnotation(Param.class);
			if (param != null) {
				String par = param.value();
				if ("".equals(par.trim())) {
					this.abortWith(method, "@Param(\"" + par + "\")这个小括号里面的值不能为空字符串");
				}
				if (!Pattern.matches(Placeholder.COLON_REG, ":" + par)) {
					this.abortWith(method, "@Param(\"" + par + "\")这个小括号里面的值只能是字母或数字组成的字符串");
				}
				params.add(param.value());
			}
		}

		String slreg = Placeholder.COLON_REG;
		String preg = Placeholder.EL_REG;

		// 把能与SL_REG和preg匹配的表达式收集起来
		Set<String> ps = new HashSet<>();
		Query[] queries = method.getAnnotationsByType(Query.class);
		for (Query query : queries) {
			String s = query.value() + query.countField() + query.countQuery();
			if (!TypeUtil.matches(s, ":\\s+").isEmpty()) {
				this.abortWith(method, "表达式不符合规范:冒号\":\"后面不能是空白");
			}

			if (!TypeUtil.matches(s, "\\$\\{?\\s+").isEmpty()) {
				this.abortWith(method, "表达式不符合规范:不能出现\"${ \" 或 \"$ \"");
			}

			if (!TypeUtil.matches(s, "\\{\\s+").isEmpty()) {
				this.abortWith(method, "表达式不符合规范:不能出现\"{ \"");
			}

			if (!TypeUtil.matches(s, "\\s+\\}").isEmpty()) {
				this.abortWith(method, "表达式不符合规范:不能出现\" }\"");
			}

			if (!TypeUtil.matches(s, "[^\\$#]\\{").isEmpty()) {
				this.abortWith(method, "表达式不符合规范:\"{\"前必须连接\"$\"或\"#\"");
			}

			// 2). 校验微笑表达式中的内容
			List<String> smiles = TypeUtil.matches(s, Placeholder.SMILE_BIG);
			for (String smile : smiles) {
				int len = TypeUtil.matches(smile, Placeholder.EL_OR_COLON).size();
				if (len != 1) {
					this.abortWith(method, "微笑表达式中的内容必须只能包含一个$表达式或一个冒号表达式,而它包含了" + len + "个表达式");
				}
			}
			smiles.clear();

			ps.addAll(TypeUtil.matchesNotrepeat(query.value(), slreg));
			ps.addAll(TypeUtil.matchesNotrepeat(query.countField(), slreg));
			ps.addAll(TypeUtil.matchesNotrepeat(query.countQuery(), slreg));

			ps.addAll(TypeUtil.matchesNotrepeat(query.value(), preg));
			ps.addAll(TypeUtil.matchesNotrepeat(query.countField(), preg));
			ps.addAll(TypeUtil.matchesNotrepeat(query.countQuery(), preg));

		}

		Condition[] conditions = method.getAnnotationsByType(Condition.class);
		for (Condition condition : conditions) {
			ps.addAll(TypeUtil.matchesNotrepeat(condition.value(), slreg));
		}

		for (String p : ps) {
			int c1 = TypeUtil.matches(p, "\\{").size();
			int c2 = TypeUtil.matches(p, "\\}").size();
			if (c1 == c2 && c1 == 0) {
				continue;
			}
			if (c1 != c2 || (c1 == c2 && c1 != 1)) { // "{" 和 "}" 必须成比出现
				this.abortWith(method, String.format("\"%s\"中的\"{\"和\"}\"分别只能出现一次或都不出现,据分析\"{\"出现%d次,而\"}\"出现%d次", p, c1, c2));
			}

			String s;
			if (Pattern.matches(slreg, p)) { // 如果能匹配上冒号表达式
				s = p.replaceFirst(":", "");
			} else {
				s = p.replace("${", "").replace("}", "").replace("$", "");
			}
			if (!params.contains(s)) {
				this.abortWith(method, String.format("发现存在%s,而从参数中没有找到@Param(\"%s\"),这种语法是不被允许的.", p, s));
			}
		}

		return method;
	}

}
