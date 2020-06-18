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

package io.crate.integrationtests;

import com.carrotsearch.randomizedtesting.annotations.Seed;
import io.crate.testing.TestingHelpers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static io.crate.testing.TestingHelpers.printedTable;
import static org.hamcrest.core.Is.is;

public class RegexpMatchesTableFunctionIntegrationTest extends SQLTransportIntegrationTest {

    @Before
    public void setup() {
        execute("create table tregex (i integer, s string) with (number_of_replicas=0)");
        execute("insert into tregex(i, s) values (?, ?)", new Object[][]{
            new Object[]{1, "foo is first"},
            new Object[]{2, "bar is second"},
            new Object[]{3, "foobar is great"},
            new Object[]{4, "boobar is greater"},
            new Object[]{5, "awam bam baluma"},
            new Object[]{6, null}
        });
        refresh();
    }

    @After
    public void teardown() {
        execute("drop table tregex");
    }

    @Seed("C328C6C2B806C0E9")
    @Test
    public void test_regexp_matches_is_used_in_select_filtering_from_a_table() {
        execute("select regexp_matches(s, '(\\w+) is (great).*', 'g') from tregex");
        Arrays.sort(response.rows(), Comparator.comparing(o -> ((List<String>) o[0]).get(0)));
        assertThat(TestingHelpers.printedTable(response.rows()), is("[boobar, great]\n" +
                                                                    "[foobar, great]\n"));
    }

    @Test
    public void test_regexp_matches_is_used_in_from_generating_a_table() {
        execute("select * from regexp_matches('foobar is greater', '(\\w+) is (great|greater)', 'g')");
        assertThat(TestingHelpers.printedTable(response.rows()), is("[foobar, great]\n"));
    }

    @Test
    public void test_regexp_matches_g() {
        execute("select regexp_matches('foobarbequebazilbarfbonk', '(b[^b]+)(b[^b]+)', 'g')");
        assertThat(printedTable(response.rows()), is("[bar, beque]\n" +
                                                     "[bazil, barf]\n"));

        execute("select groups from regexp_matches('foobarbequebazilbarfbonk', '(b[^b]+)(b[^b]+)', 'g')");
        assertThat(printedTable(response.rows()), is("[bar, beque]\n" +
                                                     "[bazil, barf]\n"));
    }

    @Test
    public void test_regexp_matches() {
        execute("select regexp_matches('foobarbequebazilbarfbonk', '(b[^b]+)(b[^b]+)')");
        assertThat(printedTable(response.rows()), is("[bar, beque]\n"));

        execute("select groups from regexp_matches('foobarbequebazilbarfbonk', '(b[^b]+)(b[^b]+)')");
        assertThat(printedTable(response.rows()), is("[bar, beque]\n"));
    }
}
