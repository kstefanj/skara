/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.skara.bots.pr;

import org.openjdk.skara.email.EmailAddress;
import org.openjdk.skara.host.*;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

class Contributors {
    private final static String addMarker = "<!-- add contributor: '%s' -->";
    private final static String removeMarker = "<!-- remove contributor: '%s' -->";
    private final static Pattern markerPattern = Pattern.compile("<!-- (add|remove) contributor: '(.*?)' -->");

    static String addContributorMarker(EmailAddress contributor) {
        return String.format(addMarker, contributor.toString());
    }

    static String removeContributorMarker(EmailAddress contributor) {
        return String.format(removeMarker, contributor.toString());
    }

    static List<EmailAddress> contributors(HostUserDetails botUser, List<Comment> comments) {
        var contributorActions = comments.stream()
                                         .filter(comment -> comment.author().equals(botUser))
                                         .map(comment -> markerPattern.matcher(comment.body()))
                                         .filter(Matcher::find)
                                         .collect(Collectors.toList());
        var contributors = new LinkedHashSet<EmailAddress>();
        for (var action : contributorActions) {
            switch (action.group(1)) {
                case "add":
                    contributors.add(EmailAddress.parse(action.group(2)));
                    break;
                case "remove":
                    contributors.remove(EmailAddress.parse(action.group(2)));
                    break;
            }
        }

        return new ArrayList<>(contributors);
    }
}
