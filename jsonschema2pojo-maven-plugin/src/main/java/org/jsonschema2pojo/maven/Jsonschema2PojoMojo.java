/**
 * Copyright © 2010-2020 Nokia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jsonschema2pojo.maven;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.utils.xml.Xpp3Dom;
import org.jsonschema2pojo.AnnotatorFactory;
import org.jsonschema2pojo.Jsonschema2Pojo;
import org.jsonschema2pojo.RuleLogger;
import org.jsonschema2pojo.SourceSortOrder;
import org.jsonschema2pojo.util.JavaVersion;
import org.jsonschema2pojo.util.URLUtil;

/**
 * When invoked, this goal reads one or more
 * <a href="http://json-schema.org/">JSON Schema</a> documents and generates DTO
 * style Java classes for data binding.
 *
 * @see <a href="http://maven.apache.org/developers/mojo-api-specification.html">Mojo API Specification</a>
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
)
public class Jsonschema2PojoMojo extends Jsonschema2PojoMojoBase {

    /**
     * Executes the plugin, to read the given source and behavioural properties
     * and generate POJOs. The current implementation acts as a wrapper around
     * the command line interface.
     */
    @Override
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = { "NP_UNWRITTEN_FIELD", "UWF_UNWRITTEN_FIELD" }, justification = "Private fields set by Maven.")
    @SuppressWarnings("PMD.UselessParentheses")
    public void execute() throws MojoExecutionException {

        addProjectDependenciesToClasspath();
        setTargetVersion();

        try {
            getAnnotationStyle();
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Not a valid annotation style: " + annotationStyle);
        }

        try {
            new AnnotatorFactory(this).getAnnotator(getCustomAnnotator());
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        if (skip) {
            return;
        }

        // verify source directories
        if (sourceDirectory != null) {
            sourceDirectory = FilenameUtils.normalize(sourceDirectory);
            // verify sourceDirectory
            try {
                URLUtil.parseURL(sourceDirectory);
            } catch (IllegalArgumentException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        } else if (!isEmpty(sourcePaths)) {
            // verify individual source paths
            for (int i = 0; i < sourcePaths.length; i++) {
                sourcePaths[i] = FilenameUtils.normalize(sourcePaths[i]);
                try {
                    URLUtil.parseURL(sourcePaths[i]);
                } catch (IllegalArgumentException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        } else {
            throw new MojoExecutionException("One of sourceDirectory or sourcePaths must be provided");
        }

        if (filteringEnabled() || (sourceDirectory != null && isEmpty(sourcePaths))) {

            if (sourceDirectory == null) {
                throw new MojoExecutionException("Source includes and excludes require the sourceDirectory property");
            }

            if (!isEmpty(sourcePaths)) {
                throw new MojoExecutionException("Source includes and excludes are incompatible with the sourcePaths property");
            }

            fileFilter = createFileFilter();
        }

        if (addCompileSourceRoot) {
            project.addCompileSourceRoot(outputDirectory.getPath());
        }

        if (useCommonsLang3) {
            getLog().warn("useCommonsLang3 is deprecated. Please remove it from your config.");
        }

        RuleLogger logger = new MojoRuleLogger(getLog());

        try {
            Jsonschema2Pojo.generate(this, logger);
        } catch (IOException e) {
            throw new MojoExecutionException("Error generating classes from JSON Schema file(s) " + sourceDirectory, e);
        }

    }

    @Override
    public String getClassNamePrefix() {
        return classNamePrefix;
    }

    @Override
    public String getClassNameSuffix() {
        return classNameSuffix;
    }

    @Override
    public String[] getFileExtensions() {
        return fileExtensions;
    }

    @Override
    public boolean isIncludeConstructors() {
        return includeConstructors;
    }

    @Override
    public boolean isConstructorsRequiredPropertiesOnly() {
        return constructorsRequiredPropertiesOnly;
    }

    @Override
    public boolean isIncludeRequiredPropertiesConstructor() {
        return includeRequiredPropertiesConstructor;
    }

    @Override
    public boolean isIncludeAllPropertiesConstructor() {
        return includeAllPropertiesConstructor;
    }

    @Override
    public boolean isIncludeCopyConstructor() {
        return includeCopyConstructor;
    }

    @Override
    public boolean isIncludeAdditionalProperties() {
        return includeAdditionalProperties;
    }

    @Override
    public boolean isIncludeGetters() { return includeGetters; }

    @Override
    public boolean isIncludeSetters() { return includeSetters; }

    @SuppressWarnings("unchecked")
    private void setTargetVersion() {
        if (isNotBlank(this.targetVersion)) {
            return;
        }

        if (project.getProperties() != null && project.getProperties().containsKey("maven.compiler.source")) {
            this.targetVersion = project.getProperties().get("maven.compiler.source").toString();
            getLog().debug("Using maven.compiler.source to set targetVersion for generated sources (" + this.targetVersion + ")");
            return;
        }

        if (project.getProperties() != null && project.getProperties().containsKey("maven.compiler.release")) {
            this.targetVersion = project.getProperties().get("maven.compiler.release").toString();
            getLog().debug("Using maven.compiler.release to set targetVersion for generated sources (" + this.targetVersion + ")");
            return;
        }

        for (Plugin p : (List<Plugin>) project.getBuildPlugins()) {
            if (p.getKey().equals("org.apache.maven.plugins:maven-compiler-plugin") && p.getConfiguration() instanceof Xpp3Dom) {
                final Xpp3Dom compilerSourceConfig = ((Xpp3Dom) p.getConfiguration()).getChild("source");
                if (compilerSourceConfig != null) {
                    this.targetVersion = compilerSourceConfig.getValue();
                    getLog().debug("Using maven-compiler-plugin 'source' to set targetVersion for generated sources (" + this.targetVersion + ")");
                    return;
                }

                final Xpp3Dom compilerReleaseConfig = ((Xpp3Dom) p.getConfiguration()).getChild("release");
                if (compilerReleaseConfig != null) {
                    this.targetVersion = compilerReleaseConfig.getValue();
                    getLog().debug("Using maven-compiler-plugin 'release' to set targetVersion for generated sources (" + this.targetVersion + ")");
                    return;
                }
            }
        }

        this.targetVersion = JavaVersion.parse(System.getProperty("java.version"));
        getLog().debug("Using JVM to set targetVersion for generated sources (" + this.targetVersion + ")");
    }

    @Override
    public String getTargetVersion() {
        return targetVersion;
    }

    @Override
    public boolean isIncludeDynamicAccessors() {
        return includeDynamicAccessors;
    }

    @Override
    public boolean isIncludeDynamicGetters() {
        return includeDynamicGetters;
    }

    @Override
    public boolean isIncludeDynamicSetters() {
        return includeDynamicSetters;
    }

    @Override
    public boolean isIncludeDynamicBuilders() {
        return includeDynamicBuilders;
    }

    @Override
    public String getDateTimeType() {
        return dateTimeType;
    }

    @Override
    public String getDateType() {
        return dateType;
    }

    @Override
    public String getTimeType() {
        return timeType;
    }

    @Override
    public boolean isUseBigIntegers() {
        return useBigIntegers;
    }

    @Override
    public boolean isUseBigDecimals() {
        return useBigDecimals;
    }

    @Override
    public boolean isFormatDateTimes() {
        return formatDateTimes;
    }

    @Override
    public boolean isFormatDates() {
        return formatDates;
    }

    @Override
    public boolean isFormatTimes() {
        return formatTimes;
    }

    @Override
    public String getCustomDatePattern() {
        return customDatePattern;
    }

    @Override
    public String getCustomTimePattern() {
        return customTimePattern;
    }

    @Override
    public String getCustomDateTimePattern() {
        return customDateTimePattern;
    }

    @Override
    public String getRefFragmentPathDelimiters() {
        return refFragmentPathDelimiters;
    }

    @Override
    public SourceSortOrder getSourceSortOrder() {
        return SourceSortOrder.valueOf(sourceSortOrder.toUpperCase());
    }

    @Override
    public Map<String, String> getFormatTypeMapping() {
        return formatTypeMapping;
    }

    @Override
    public boolean isUseInnerClassBuilders() {
        return useInnerClassBuilders;
    }

    @Override
    public boolean isIncludeGeneratedAnnotation() {
        return includeGeneratedAnnotation;
    }

    @Override
    public boolean isUseJakartaValidation() {
        return useJakartaValidation;
    }
}
