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

package eu.fasten.analyzer.restapiplugin.mvn.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mvn/packages")
public class ResolutionApi {

    private final ResolutionApiService service;

    public ResolutionApi(ResolutionApiService service) {
        this.service = service;
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/resolve/dependencies", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> resolveDependencies(@PathVariable("pkg") String package_name,
                                               @PathVariable("pkg_ver") String package_version,
                                               @RequestParam(required = false, defaultValue = "true") boolean transitive,
                                               @RequestParam(required = false, defaultValue = "-1") long timestamp,
                                               @RequestParam(required = false, defaultValue = "false") boolean useDepGraph) {
        return service.resolveDependencies(package_name, package_version, transitive, timestamp, useDepGraph);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/resolve/dependents", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> resolveDependents(@PathVariable("pkg") String package_name,
                                             @PathVariable("pkg_ver") String package_version,
                                             @RequestParam(required = false, defaultValue = "true") boolean transitive,
                                             @RequestParam(required = false, defaultValue = "-1") long timestamp) {
        return service.resolveDependents(package_name, package_version, transitive, timestamp);
    }
}
