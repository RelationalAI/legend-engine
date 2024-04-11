// Copyright 2020 Goldman Sachs
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

package org.finos.legend.engine.application.query.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.StereotypePtr;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.TaggedValue;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Query
{
    public String id;
    public String name;
    public String description;
    public String groupId;
    public String artifactId;
    public String versionId;
    public String originalVersionId;
    public String mapping;
    public String runtime;
    public String content;
    public Long lastUpdatedAt;
    public Long createdAt;

    public List<TaggedValue> taggedValues;
    public List<StereotypePtr> stereotypes;

    public List<QueryParameterValue> defaultParameterValues;

    // We make it clear that we only allow a single owner
    public String owner;

    public Map<String, ?> gridConfig;
}
