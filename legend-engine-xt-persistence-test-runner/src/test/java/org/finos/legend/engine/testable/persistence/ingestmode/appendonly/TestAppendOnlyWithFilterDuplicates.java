// Copyright 2022 Goldman Sachs
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

package org.finos.legend.engine.testable.persistence.ingestmode.appendonly;

import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestPassed;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestResult;
import org.finos.legend.engine.testable.persistence.ingestmode.TestPersistenceBase;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TestAppendOnlyWithFilterDuplicates extends TestPersistenceBase
{
    @Test
    public void testAppendOnlyWithNoAuditing() throws Exception
    {
        String path = "src/test/resources/append-only/filter_duplicates/persistence_no_audit.txt";
        String persistenceSpec = readPureCode(path);
        TestResult result = testPersistence(persistenceSpec).results.get(0);

        assertTrue(result instanceof TestPassed);
        Assert.assertEquals("test::TestPersistence", ((TestPassed) result).testable);
    }

    @Test
    public void testAppendOnlyWithDateTimeAuditing() throws Exception
    {
        String path = "src/test/resources/append-only/filter_duplicates/persistence_date_time_auditing.txt";
        String persistenceSpec = readPureCode(path);
        TestResult result = testPersistence(persistenceSpec).results.get(0);

        assertTrue(result instanceof TestPassed);
        Assert.assertEquals("test::TestPersistence", ((TestPassed) result).testable);
    }
}
