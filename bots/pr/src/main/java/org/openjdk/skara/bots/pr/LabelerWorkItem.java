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

import org.openjdk.skara.bot.WorkItem;
import org.openjdk.skara.host.PullRequest;
import org.openjdk.skara.vcs.Hash;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LabelerWorkItem implements WorkItem {
    private final PullRequest pr;
    private final Map<String, List<Pattern>> labelPatterns;
    private final ConcurrentMap<Hash, Boolean> currentLabels;

    LabelerWorkItem(PullRequest pr, Map<String, List<Pattern>> labelPatterns, ConcurrentMap<Hash, Boolean> currentLabels) {
        this.pr = pr;
        this.labelPatterns = labelPatterns;
        this.currentLabels = currentLabels;
    }

    @Override
    public String toString() {
        return "LabelerWorkItem@" + pr.repository().getName() + "#" + pr.getId();
    }

    @Override
    public boolean concurrentWith(WorkItem other) {
        if (!(other instanceof LabelerWorkItem)) {
            return true;
        }
        LabelerWorkItem otherItem = (LabelerWorkItem) other;
        if (!pr.getId().equals(otherItem.pr.getId())) {
            return true;
        }
        if (!pr.repository().getName().equals(otherItem.pr.repository().getName())) {
            return true;
        }
        return false;
    }

    private Set<String> getLabels(PullRequestInstance prInstance) throws IOException {
        var labels = new HashSet<String>();
        var files = prInstance.changedFiles();
        for (var file : files) {
            for (var label : labelPatterns.entrySet()) {
                for (var pattern : label.getValue()) {
                    var matcher = pattern.matcher(file.toString());
                    if (matcher.find()) {
                        labels.add(label.getKey());
                        break;
                    }
                }
            }
        }
        return labels;
    }

    @Override
    public void run(Path scratchPath) {
        if (currentLabels.containsKey(pr.getHeadHash())) {
            return;
        }
        try {
            var prInstance = new PullRequestInstance(scratchPath.resolve("labeler"), pr);
            var newLabels = getLabels(prInstance);
            var currentLabels = pr.getLabels().stream()
                    .filter(labelPatterns::containsKey)
                    .collect(Collectors.toSet());

            // Add all labels not already set
            newLabels.stream()
                     .filter(label -> !currentLabels.contains(label))
                     .forEach(pr::addLabel);

            // Remove set labels no longer present
            currentLabels.stream()
                         .filter(label -> !newLabels.contains(label))
                         .forEach(pr::removeLabel);

            this.currentLabels.put(pr.getHeadHash(), Boolean.TRUE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }
}
