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
package org.openjdk.skara.bots.mlbridge;

import org.openjdk.skara.email.EmailAddress;
import org.openjdk.skara.host.HostedRepository;
import org.openjdk.skara.host.network.URIBuilder;
import org.openjdk.skara.vcs.*;
import org.openjdk.skara.webrev.Webrev;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.Comparator;

class WebrevStorage {
    private final HostedRepository storage;
    private final String storageRef;
    private final Path baseFolder;
    private final URI baseUri;
    private final EmailAddress author;

    WebrevStorage(HostedRepository storage, String ref, Path baseFolder, URI baseUri, EmailAddress author) {
        this.baseFolder = baseFolder;
        this.baseUri = baseUri;
        this.storage = storage;
        storageRef = ref;
        this.author = author;
    }

    private void generate(PullRequestInstance prInstance, Path folder, Hash base, Hash head) throws IOException {
        Files.createDirectories(folder);
        Webrev.repository(prInstance.localRepo()).output(folder)
              .generate(base, head);
    }

    private void push(Repository localStorage, Path webrevFolder) throws IOException {
        var files = Files.walk(webrevFolder).toArray(Path[]::new);
        localStorage.add(files);
        var hash = localStorage.commit("Added webrev", author.fullName().orElseThrow(), author.address());
        localStorage.push(hash, storage.getUrl(), storageRef);
    }

    private static void clearDirectory(Path directory) {
        try {
            Files.walk(directory)
                 .map(Path::toFile)
                 .sorted(Comparator.reverseOrder())
                 .forEach(File::delete);
        } catch (IOException io) {
            throw new RuntimeException(io);
        }
    }

    URI createAndArchive(PullRequestInstance prInstance, Path scratchPath, Hash base, Hash head, String identifier) {
        try {
            var localStorage = Repository.materialize(scratchPath, storage.getUrl(), storageRef);
            var relativeFolder = baseFolder.resolve(String.format("%s/webrev.%s", prInstance.id(), identifier));
            var outputFolder = scratchPath.resolve(relativeFolder);
            // If a previous operation was interrupted there may be content here already - overwrite if so
            if (Files.exists(outputFolder)) {
                clearDirectory(outputFolder);
            }
            generate(prInstance, outputFolder, base, head);
            if (!localStorage.isClean()) {
                push(localStorage, outputFolder);
            }
            return URIBuilder.base(baseUri).appendPath(relativeFolder.toString()).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
