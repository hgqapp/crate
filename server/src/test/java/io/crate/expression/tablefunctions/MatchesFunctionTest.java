/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.expression.tablefunctions;

import io.crate.metadata.Scalar;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;

import java.util.Locale;

public class MatchesFunctionTest extends AbstractTableFunctionsTest {

    @Test
    public void testCompile() throws Exception {
        Matcher<Scalar> matcher = new BaseMatcher<>() {
            @Override
            public boolean matches(Object item) {
                MatchesFunction regexpImpl = (MatchesFunction) item;
                // ensure that the RegexMatcher was created due to compilation
                return regexpImpl.regexMatcher() != null;
            }

            @Override
            public void describeTo(Description description) {
            }
        };
        assertCompile("regexp_matches(name, '.*(ba).*')", (s) -> matcher);
    }

    private static String regexp_matches(String value, String pattern, String flags) {
        return String.format(Locale.ENGLISH, "regexp_matches('%s', '%s', '%s')", value, pattern, flags);
    }

    private static String regexp_matches(String value, String pattern) {
        return String.format(Locale.ENGLISH, "regexp_matches('%s', '%s')", value, pattern);
    }

    @Test
    public void test_execute_with_flags() throws Exception {
        assertExecute(regexp_matches("foobarbequebaz bar", ".*(ba).*", "us"), "[ba]\n");
    }

    @Test
    public void test_execute_with_g_flag() throws Exception {
        String value = "#abc #abc   #def #def #ghi #ghi";
        String pattern = "(#[^\\s]*) (#[^\\s]*)";
        String result = "[#abc, #abc]\n" +
                        "[#def, #def]\n" +
                        "[#ghi, #ghi]\n";
        assertExecute(regexp_matches(value, pattern, "g"), result);

        pattern = "#[^\\s]* #[^\\s]*";
        result = "[#abc #abc]\n" +
                 "[#def #def]\n" +
                 "[#ghi #ghi]\n";
        assertExecute(regexp_matches(value, pattern, "g"), result);
    }

    @Test
    public void test_execute_without_g_flag() throws Exception {
        String value = "#abc #abc   #def #def #ghi #ghi";
        String pattern = "(#[^\\s]*) (#[^\\s]*)";
        assertExecute(regexp_matches(value, pattern), "[#abc, #abc]\n");

        pattern = "#[^\\s]* #[^\\s]*";
        assertExecute(regexp_matches(value, pattern), "[#abc #abc]\n");
    }

    @Test
    public void test_execute_postgres_example() {
        String result = "[bar, beque]\n" +
                        "[bazil, barf]\n";
        assertExecute(regexp_matches("foobarbequebazilbarfbonk", "(b[^b]+)(b[^b]+)", "g"), result);
    }
}
