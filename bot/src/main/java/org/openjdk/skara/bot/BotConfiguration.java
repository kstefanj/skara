/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.skara.bot;

import org.openjdk.skara.census.Census;
import org.openjdk.skara.host.HostedRepository;
import org.openjdk.skara.json.JSONObject;

import java.nio.file.Path;
import java.util.Optional;

public interface BotConfiguration {

    /**
     * Folder that WorkItems may use to store permanent data.
     * @return
     */
    Path storageFolder();

    /**
     * Configuration-specific name mapped to a HostedRepository.
     * @param name
     * @return
     */
    HostedRepository repository(String name);

    /**
     * Additional bot-specific configuration.
     * @return
     */
    JSONObject specific();
}
