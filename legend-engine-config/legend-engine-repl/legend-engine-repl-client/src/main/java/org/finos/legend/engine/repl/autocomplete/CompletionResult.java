// Copyright 2024 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.engine.repl.autocomplete;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;

public class CompletionResult
{
    private final EngineException engineException;
    private final MutableList<CompletionItem> completion;

    public CompletionResult(EngineException e)
    {
        this.engineException = e;
        this.completion = Lists.mutable.empty();
    }

    public CompletionResult(MutableList<CompletionItem> completion)
    {
        this.engineException = null;
        this.completion = completion;
    }

    public EngineException getEngineException()
    {
        return engineException;
    }

    public MutableList<CompletionItem> getCompletion()
    {
        return completion;
    }
}
