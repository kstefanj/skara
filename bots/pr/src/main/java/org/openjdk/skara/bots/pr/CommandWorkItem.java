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
import org.openjdk.skara.host.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;
import java.util.stream.*;

public class CommandWorkItem implements WorkItem {
    private final PullRequest pr;
    private final HostedRepository censusRepo;
    private final String censusRef;
    private final Map<String, String> external;

    private final Logger log = Logger.getLogger("org.openjdk.skara.bots.pr");

    private final String commandReplyMarker = "<!-- Jmerge command reply message (%s) -->";
    private final Pattern commandReplyPattern = Pattern.compile("<!-- Jmerge command reply message \\((\\S+)\\) -->");

    private final static Map<String, CommandHandler> commandHandlers = Map.of(
            "help", new HelpCommand(),
            "integrate", new IntegrateCommand(),
            "sponsor", new SponsorCommand(),
            "contributor", new ContributorCommand(),
            "summary", new SummaryCommand()
    );

    static class HelpCommand implements CommandHandler {
        static private Map<String, String> external = null;

        @Override
        public void handle(PullRequest pr, CensusInstance censusInstance, Path scratchPath,  String args, Comment comment, List<Comment> allComments, PrintWriter reply) {
            reply.println("Available commands:");
            Stream.concat(
                    commandHandlers.entrySet().stream()
                                   .map(entry -> entry.getKey() + " - " + entry.getValue().description()),
                    external.entrySet().stream()
                            .map(entry -> entry.getKey() + " - " + entry.getValue())
            ).sorted().forEachOrdered(command -> reply.println(" * " + command));
        }

        @Override
        public String description() {
            return "shows this text";
        }
    }

    CommandWorkItem(PullRequest pr, HostedRepository censusRepo, String censusRef, Map<String, String> external) {
        this.pr = pr;
        this.censusRepo = censusRepo;
        this.censusRef = censusRef;
        this.external = external;

        if (HelpCommand.external == null) {
            HelpCommand.external = external;
        }
    }

    private List<AbstractMap.SimpleEntry<String, Comment>> findCommandComments(List<Comment> comments) {
        var self = pr.repository().host().getCurrentUserDetails();
        var handled = comments.stream()
                              .filter(comment -> comment.author().equals(self))
                              .map(comment -> commandReplyPattern.matcher(comment.body()))
                              .filter(Matcher::find)
                              .map(matcher -> matcher.group(1))
                              .collect(Collectors.toSet());

        var commandPattern = Pattern.compile("^/(.*)");

        return comments.stream()
                       .filter(comment -> !comment.author().equals(self))
                       .map(comment -> new AbstractMap.SimpleEntry<>(comment, commandPattern.matcher(comment.body())))
                       .filter(entry -> entry.getValue().find())
                       .filter(entry -> !handled.contains(entry.getKey().id()))
                       .map(entry -> new AbstractMap.SimpleEntry<>(entry.getValue().group(1), entry.getKey()))
                       .collect(Collectors.toList());
    }

    private void processCommand(PullRequest pr, CensusInstance censusInstance, Path scratchPath, String command, Comment comment, List<Comment> allComments) {
        var writer = new StringWriter();
        var printer = new PrintWriter(writer);

        printer.println(String.format(commandReplyMarker, comment.id()));
        printer.print("@");
        printer.print(comment.author().userName());
        printer.print(" ");

        command = command.strip();
        var argSplit = command.indexOf(' ');
        var commandWord = argSplit > 0 ? command.substring(0, argSplit) : command;
        var commandArgs = argSplit > 0 ? command.substring(argSplit + 1) : "";

        var handler = commandHandlers.get(commandWord);
        if (handler != null) {
            handler.handle(pr, censusInstance, scratchPath, commandArgs, comment, allComments, printer);
        } else {
            if (!external.containsKey(commandWord)) {
                printer.print("Unknown command `");
                printer.print(command);
                printer.println("` - for a list of valid commands use `/help`.");
            } else {
                // Do not reply to external commands
                return;
            }
        }

        pr.addComment(writer.toString());
    }

    @Override
    public boolean concurrentWith(WorkItem other) {
        if (!(other instanceof CommandWorkItem)) {
            return true;
        }
        CommandWorkItem otherItem = (CommandWorkItem)other;
        if (!pr.getId().equals(otherItem.pr.getId())) {
            return true;
        }
        if (!pr.repository().getName().equals(otherItem.pr.repository().getName())) {
            return true;
        }
        return false;
    }

    @Override
    public void run(Path scratchPath) {
        log.info("Looking for merge commands");

        var comments = pr.getComments();

        var unprocessedCommands = findCommandComments(comments);
        if (unprocessedCommands.isEmpty()) {
            log.fine("No new merge commands found, stopping further processing");
            return;
        }

        var census = CensusInstance.create(censusRepo, censusRef, scratchPath.resolve("census"), pr);
        for (var entry : unprocessedCommands) {
            processCommand(pr, census, scratchPath.resolve("pr"), entry.getKey(), entry.getValue(), comments);
        }
    }
}
