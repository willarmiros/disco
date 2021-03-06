/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package software.amazon.disco.agent.interception.templates.integtest.source;

public class ExampleOuterClass {
    private final ExampleDelegatedClass delegatedClass;

    public ExampleOuterClass(ExampleDelegatedClass delegatedClass) {
        this.delegatedClass = delegatedClass;
    }

    public String getValue() {
        return "Outer";
    }

    public ExampleDelegatedClass getDelegate() {
        return delegatedClass;
    }

    public ExampleDelegatedClass getDelegate(int key) {
        if (key == 42) {
            return delegatedClass;
        } else {
            return null;
        }
    }
}
