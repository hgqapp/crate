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

package io.crate.expression.scalar.regex;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class RegexMatcher {

    private final boolean globalFlag;
    private final Pattern pattern;
    private Matcher matcher;

    public RegexMatcher(String regex, @Nullable String flags) {
        pattern = Pattern.compile(regex, parseFlags(flags));
        globalFlag = isGlobal(flags);
    }

    public RegexMatcher(String regex) {
        this(regex, null);
    }

    public List<List<String>> match(String term) {
        initMatcher(term);
        List<List<String>> result = null;
        if (globalFlag) {
            result = Stream
                .generate(this::groups)
                .takeWhile(Objects::nonNull)
                .collect(Collectors.toList());
            if (result.isEmpty()) {
                result = null;
            }
        } else {
            List<String> groups = groups();
            if (groups != null) {
                result = List.of(groups);
            }
        }
        return result;
    }

    @Nullable
    private List<String> groups() {
        if (!matcher.find()) {
            return null;
        }
        int groupCount = matcher.groupCount();
        if (groupCount == 0) {
            try {
                return List.of(matcher.group());
            } catch (IllegalStateException e) {
                return null;
            }
        }
        return IntStream
            .range(1, groupCount + 1)
            .mapToObj(matcher::group)
            .collect(Collectors.toList());
    }

    public String replace(String term, String replacement) {
        initMatcher(term);
        if (globalFlag) {
            return matcher.replaceAll(replacement);
        } else {
            return matcher.replaceFirst(replacement);
        }
    }

    private void initMatcher(String term) {
        if (matcher == null) {
            matcher = pattern.matcher(term);
        } else {
            matcher.reset(term);
        }
    }

    private static int parseFlags(@Nullable String flagsString) {
        int flags = 0;
        if (flagsString == null) {
            return flags;
        }
        for (char flag : flagsString.toCharArray()) {
            switch (flag) {
                case 'i':
                    flags = flags | Pattern.CASE_INSENSITIVE;
                    break;
                case 'u':
                    flags = flags | Pattern.UNICODE_CASE;
                    break;
                case 'U':
                    flags = flags | Pattern.UNICODE_CHARACTER_CLASS;
                    break;
                case 's':
                    flags = flags | Pattern.DOTALL;
                    break;
                case 'm':
                    flags = flags | Pattern.MULTILINE;
                    break;
                case 'x':
                    flags = flags | Pattern.COMMENTS;
                    break;
                case 'd':
                    flags = flags | Pattern.UNIX_LINES;
                    break;
                case ' ':
                case 'g':
                    // handled in isGlobalFunction
                    break;
                default:
                    throw new IllegalArgumentException("The regular expression flag is unknown: " + flag);
            }
        }

        return flags;
    }

    public static boolean isGlobal(@Nullable String flags) {
        if (flags == null) {
            return false;
        }
        return flags.indexOf('g') != -1;
    }


    // PCRE features
    private static final String CHARACTER_CLASSES = "dDsSwW";
    private static final String BOUNDARY_MATCHERS = "bBAGZz";
    private static final String EMBEDDED_FLAGS = "idmsuxU";

    // recognize pcre escaped sequences anywhere inside the pattern
    private static final String ESCAPE_SEQUENCES_PATTERN = ".*\\\\[" + CHARACTER_CLASSES + BOUNDARY_MATCHERS + "].*";

    // recognize pcre embedded flags at the beginning of the pattern
    private static final String EMBEDDED_FLAGS_PATTERN = "^\\(\\?[" + EMBEDDED_FLAGS + "]\\).*";

    // final precompiled java.util.regex.Pattern
    private static final Pattern PCRE_PATTERN = Pattern.compile(ESCAPE_SEQUENCES_PATTERN + "|" + EMBEDDED_FLAGS_PATTERN);

    /**
     * Determine whether regex pattern contains PCRE features, e.g.
     * - predefined character classes like \d, \D, \s, \S, \w, \W
     * - boundary matchers like \b, \B, \A, \G, \Z, \z
     * - embedded flag expressions like (?i), (?d), etc.
     *
     * @see java.util.regex.Pattern
     */
    public static boolean isPcrePattern(String pattern) {
        return PCRE_PATTERN.matcher(pattern).matches();
    }
}
