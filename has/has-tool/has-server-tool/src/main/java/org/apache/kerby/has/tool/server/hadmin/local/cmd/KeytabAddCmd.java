/**
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.kerby.has.tool.server.hadmin.local.cmd;

import org.apache.kerby.has.common.HasException;
import org.apache.kerby.has.server.admin.LocalHasAdmin;

import java.io.File;
import java.util.List;

public class KeytabAddCmd extends HadminCmd {
    private static final String USAGE =
        "Usage: ktadd [-k[eytab] keytab] [-q] [-e keysaltlist] [-norandkey] [principal | -glob princ-exp] [...]";

    private static final String DEFAULT_KEYTAB_FILE_LOCATION = "/etc/krb5.keytab";

    public KeytabAddCmd(LocalHasAdmin hadmin) {
        super(hadmin);
    }

    @Override
    public void execute(String[] items) {

        String principal = null;
        String keytabFileLocation = null;
        Boolean glob = false;

        //Since commands[0] is ktadd, the initial index is 1.
        int index = 1;
        while (index < items.length) {
            String command = items[index];
            if (command.equals("-k")) {
                index++;
                if (index >= items.length) {
                    System.err.println(USAGE);
                    return;
                }
                keytabFileLocation = items[index].trim();

            } else if (command.equals("-glob")) {
                glob = true;
            } else if (!command.startsWith("-")) {
                principal = command;
            }
            index++;
        }

        if (keytabFileLocation == null) {
            keytabFileLocation = DEFAULT_KEYTAB_FILE_LOCATION;
        }
        File keytabFile = new File(keytabFileLocation);

        if (principal == null) {
            System.out.println((glob ? "princ-exp" : "principal") + " not specified!");
            System.err.println(USAGE);
            return;
        }

        try {
            if (glob) {
                List<String> principals = getHadmin().getPrincipals(principal);
                if (principals.size() != 0) {
                    getHadmin().exportKeytab(keytabFile, principals);
                }
            } else {
                getHadmin().exportKeytab(keytabFile, principal);
            }
            System.out.println("Principal export to keytab file : " + keytabFile + " successful .");
        } catch (HasException e) {
            System.err.println("Principal \"" + principal + "\" fail to add entry to keytab."
                    + e.getMessage());
        }
    }
}