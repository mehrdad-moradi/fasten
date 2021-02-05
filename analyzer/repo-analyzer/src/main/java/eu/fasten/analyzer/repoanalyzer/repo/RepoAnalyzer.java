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

package eu.fasten.analyzer.repoanalyzer.repo;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.nodeTypes.NodeWithMembers;
import com.github.javaparser.ast.stmt.BlockStmt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class RepoAnalyzer {

    private static final String DEFAULT_TESTS_PATH = "/src/test/java";
    private static final String DEFAULT_SOURCES_PATH = "/src/main/java";

    private final String rootPath;
    private final List<Path> moduleRoots;

    /**
     * Constructs a Repo Analyzer given a path the root of a repository to analyze.
     *
     * @param path path to the repository
     */
    public RepoAnalyzer(final String path) throws IOException {
        this.rootPath = path;
        this.moduleRoots = extractModuleRoots(Path.of(path));
    }

    /**
     * Analyses tests in repository.
     *
     * @return JSON with statistics of the repository
     * @throws IOException if I/O exception occurs when accessing root file
     */
    public JSONObject analyze() throws IOException {
        var payload = new JSONObject();
        payload.put("repoPath", this.rootPath);

        var results = new JSONArray();
        for (var module : this.moduleRoots) {
            var statistics = new JSONObject();
            statistics.put("path", module.toAbsolutePath());

            var sourceFiles = getMatchingFiles(getPathToSourcesRoot(module), List.of("^.*\\.java"));
            statistics.put("sourceFiles", sourceFiles.size());

            var testFiles = getMatchingFiles(getPathToTestsRoot(module), getTestsPatterns());
            var testBodies = getJUnitTests(testFiles);
            testFiles = testBodies.keySet();
            statistics.put("testFiles", testFiles.size());

            var sourceFunctions = getNumberOfFunctions(sourceFiles);
            statistics.put("numberOfFunctions", sourceFunctions);

            var testToSourceRatio = roundTo3((double) testFiles.size() / (double) sourceFiles.size());
            statistics.put("testToSourceRatio", testToSourceRatio);

            var numberOfUnitTests = testBodies.values().stream()
                    .map(List::size)
                    .reduce(0, Integer::sum);
            statistics.put("numberOfUnitTests", numberOfUnitTests);

            var unitTestsToFunctionsRatio = roundTo3((double) numberOfUnitTests / (double) sourceFunctions);
            statistics.put("unitTestsToFunctionsRatio", unitTestsToFunctionsRatio);

            var mockImportFiles = getFilesWithMockImport(testFiles);
            statistics.put("filesWithMockImport", mockImportFiles.size());

            var testWithMocks = getTestsWithMock(testBodies, mockImportFiles);
            int numberOfUnitTestsWithMocks = testWithMocks.values().stream()
                    .map(List::size)
                    .reduce(0, Integer::sum);
            statistics.put("unitTestsWithMocks", numberOfUnitTestsWithMocks);

            var mockingRatio = roundTo3((double) numberOfUnitTestsWithMocks / (double) numberOfUnitTests);
            statistics.put("unitTestsMockingRatio", mockingRatio);

            if (sourceFiles.size() > 0) {
                results.put(statistics);
            }
        }
        payload.put("modules", results);

        return payload;
    }

    /**
     * Recursively get a list of files that have a name that matches one of the regular expressions.
     *
     * @param directory root to start searching from
     * @param patterns  list of regular expressions
     * @return list of files
     */
    private Set<Path> getMatchingFiles(final Path directory, final List<String> patterns) {
        var predicate = patterns.stream()
                .map(p -> Pattern.compile(p).asPredicate())
                .reduce(x -> false, Predicate::or);
        try {
            return Files.walk(directory)
                    .filter(f -> predicate.test(f.getFileName().toString()))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return new HashSet<>();
        }
    }

    /**
     * Get absolute path to the source files root. Extracts source file directory from pom.xml or
     * uses Maven default path.
     *
     * @param root root directory
     * @return root of the source files
     */
    private Path getPathToSourcesRoot(final Path root) throws IOException {
        var pomContent = Files.readString(Path.of(root.toString(), "pom.xml"));
        var sourcePath = StringUtils.substringBetween(pomContent, "<sourceDirectory>", "</sourceDirectory>");
        sourcePath = sourcePath == null ? DEFAULT_SOURCES_PATH : sourcePath;
        while (sourcePath.contains("$")) {
            sourcePath = sourcePath.replaceFirst("\\$\\{.*}", "");
        }
        return Path.of(root.toAbsolutePath().toString(), sourcePath);
    }

    /**
     * Get absolute path to the test files root. Extracts test file directory from pom.xml or
     * uses Maven default path.
     *
     * @param root root directory
     * @return root of the test files
     */
    private Path getPathToTestsRoot(final Path root) throws IOException {
        var pomContent = Files.readString(Path.of(root.toString(), "pom.xml"));
        var sourcePath = StringUtils.substringBetween(pomContent, "<testSourceDirectory>", "</testSourceDirectory>");
        sourcePath = sourcePath == null ? DEFAULT_TESTS_PATH : sourcePath;
        while (sourcePath.contains("$")) {
            sourcePath = sourcePath.replaceFirst("\\$\\{.*}", "");
        }
        return Path.of(root.toAbsolutePath().toString(), sourcePath);
    }

    /**
     * Get a list of default Maven regular expressions that match test files names.
     * Extracts additional regular expressions from pom.xml.
     *
     * @return list of regular expressions
     */
    private List<String> getTestsPatterns() {
        // TODO: take into account custom regex configurations of maven surefire plugin
        // https://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html

        var patterns = new ArrayList<String>();

        patterns.add("^.*Test\\.java");
        patterns.add("^Test.*\\.java");
        patterns.add("^.*Tests\\.java");
        patterns.add("^.*TestCase\\.java");

        return patterns;
    }

    /**
     * Get a map of files as keys and a list of test bodies as value.
     *
     * @param testClasses paths to test classes
     * @return a map of files and test bodies
     * @throws IOException if I/O exception occurs when reading a file
     */
    private Map<Path, List<BlockStmt>> getJUnitTests(final Set<Path> testClasses) throws IOException {
        var parser = new JavaParser();
        var testBodies = new HashMap<Path, List<BlockStmt>>();

        for (var testClass : testClasses) {
            var content = parser.parse(testClass)
                    .getResult()
                    .orElseThrow(IOException::new);

            var methods = content.findAll(MethodDeclaration.class)
                    .stream()
                    .filter(t -> t.getAnnotations()
                            .stream()
                            .anyMatch(a -> a.getName().equals(new Name("Test"))))
                    .map(t -> t.getBody().orElse(new BlockStmt()))
                    .collect(Collectors.toList());

            if (!methods.isEmpty()) {
                testBodies.put(testClass, methods);
            }
        }
        return testBodies;
    }

    /**
     * Get number of functions in source files.
     *
     * @param sourceFiles paths to source files
     * @return number of source files
     * @throws IOException if I/O exception occurs when reading a file
     */
    private int getNumberOfFunctions(final Set<Path> sourceFiles) throws IOException {
        var parser = new JavaParser();
        int methodCounter = 0;

        for (var testClass : sourceFiles) {
            var content = parser.parse(testClass)
                    .getResult()
                    .orElseThrow(IOException::new);

            methodCounter += content.findAll(ClassOrInterfaceDeclaration.class)
                    .stream()
                    .map(NodeWithMembers::getMethods)
                    .map(List::size)
                    .reduce(0, Integer::sum);
        }
        return methodCounter;
    }

    /**
     * Get a list of files that have imported a mock framework.
     *
     * @param testClasses paths to test classes
     * @return a list of files with mock import
     * @throws IOException if I/O exception occurs when reading a file
     */
    private List<Path> getFilesWithMockImport(final Set<Path> testClasses) throws IOException {
        var files = new ArrayList<Path>();

        for (var testClass : testClasses) {
            var content = Files.readString(testClass);

            var header = content.split("class", 2)[0];
            var pattern = Pattern.compile("import[^;]*Mock.*;");
            if (pattern.matcher(header).find()) {
                files.add(testClass);
            }
        }
        return files;
    }

    /**
     * Get a map of files and respective test bodies that contain keywords of mocking frameworks.
     *
     * @param testBodies          all test bodies
     * @param filesWithMockImport files that have mock imports
     * @return map of files and test bodies with mocks
     */
    private Map<Path, List<BlockStmt>> getTestsWithMock(final Map<Path, List<BlockStmt>> testBodies,
                                                        final List<Path> filesWithMockImport) {
        var tests = new HashMap<Path, List<BlockStmt>>();

        var patterns = new String[]{
                "\\.mock\\(", "\\.when\\(", "\\.spy\\(", "\\.doNothing\\(", // Mockito
                "replayAll\\(\\)", "verifyAll\\(\\)", "\\.createMock\\(", // EasyMock
                "@Mocked", "new Expectations\\(\\)"}; // JMockit
        var predicate = Arrays.stream(patterns)
                .map(p -> Pattern.compile(p).asPredicate())
                .reduce(x -> false, Predicate::or);

        for (var file : filesWithMockImport) {
            tests.put(file, testBodies.get(file).stream().filter(t -> predicate.test(t.toString())).collect(Collectors.toList()));
        }

        return tests;
    }

    /**
     * Extract paths to all modules of the project.
     *
     * @param root root directory
     * @return a list of paths to modules
     */
    private List<Path> extractModuleRoots(final Path root) throws IOException {
        var moduleRoots = new ArrayList<Path>();

        var pomContent = Files.readString(Path.of(root.toAbsolutePath().toString(), "pom.xml"));
        var modules = StringUtils.substringBetween(pomContent, "<modules>", "</modules>");

        if (modules == null) {
            moduleRoots.add(root);
            return moduleRoots;
        }

        var moduleTags = modules.split("</module>");
        var moduleNames = Arrays.stream(moduleTags)
                .filter(t -> t.contains("<module>"))
                .map(t -> t.substring(t.indexOf("<module>") + 8))
                .map(t -> Path.of(root.toAbsolutePath().toString(), t))
                .collect(Collectors.toList());
        for (var module : moduleNames) {
            moduleRoots.addAll(extractModuleRoots(module));
        }
        return moduleRoots;
    }

    /**
     * Rounds value with precision 3.
     *
     * @param value value to round
     * @return rounded value
     */
    private double roundTo3(double value) {
        double multiplier = Math.pow(10, 3);
        return (double) Math.round(multiplier * value) / multiplier;
    }
}
