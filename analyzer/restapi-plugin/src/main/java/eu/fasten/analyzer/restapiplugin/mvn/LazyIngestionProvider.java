/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.analyzer.restapiplugin.mvn;

import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.MavenResolver;
import org.json.JSONObject;
import java.sql.Timestamp;

public class LazyIngestionProvider {

    private static boolean hasArtifactBeenIngested(String packageName, String version) {
        return KnowledgeBaseConnector.kbDao.isArtifactIngested(packageName, version);
    }

    public static void ingestArtifactIfNecessary(String packageName, String version, String artifactRepo, Long date) {
        if (!hasArtifactBeenIngested(packageName, version)) {
            var jsonRecord = new JSONObject();
            jsonRecord.put("groupId", packageName.split(Constants.mvnCoordinateSeparator)[0]);
            jsonRecord.put("artifactId", packageName.split(Constants.mvnCoordinateSeparator)[1]);
            jsonRecord.put("version", version);
            if (artifactRepo != null && !artifactRepo.isEmpty()) {
                jsonRecord.put("artifactRepository", artifactRepo);
            }
            if (date != null && date > 0) {
                jsonRecord.put("date", date);
            }
            var id = KnowledgeBaseConnector.kbDao.insertIngestedArtifact(packageName, version, new Timestamp(System.currentTimeMillis()));
            if (id != -1 && KnowledgeBaseConnector.kafkaProducer != null && KnowledgeBaseConnector.ingestTopic != null) {
                KafkaWriter.sendToKafka(KnowledgeBaseConnector.kafkaProducer, KnowledgeBaseConnector.ingestTopic, jsonRecord.toString());
            }
        }
    }

    public static void ingestArtifactWithDependencies(String packageName, String version) {
        var groupId = packageName.split(Constants.mvnCoordinateSeparator)[0];
        var artifactId = packageName.split(Constants.mvnCoordinateSeparator)[0];
        var mavenResolver = new MavenResolver();
        var dependencies = mavenResolver.resolveFullDependencySetOnline(groupId, artifactId, version);
        // TODO: Provide proper support for different artifact repositories
        ingestArtifactIfNecessary(packageName, version, null, null);
        dependencies.forEach(d -> ingestArtifactIfNecessary(d.groupId + Constants.mvnCoordinateSeparator + d.artifactId, d.version.toString(), null, null));
    }
}
