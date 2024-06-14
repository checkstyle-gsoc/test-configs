package com.example.extractor;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConfigSerializer {

    public static void serializeConfig(String exampleFilePath, String templateFilePath, String outputFilePath) throws Exception {
        System.out.println("Loading configuration from example file...");
        TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
        Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();

        String template = new String(Files.readAllBytes(Path.of(templateFilePath)));

        // Accumulate values
        Configuration treeWalkerModule = getTreeWalkerModule(xmlConfig);
        String moduleContent = treeWalkerModule != null ? buildModuleContent(treeWalkerModule, "        ") : "";

        // Call replacePlaceholders to get the final config content
        String configContent = TemplateProcessor.replacePlaceholders(template, moduleContent);

        // Write the final config content to the output file
        Files.write(Path.of(outputFilePath), configContent.getBytes());
    }

    public static void serializeAllInOneConfig(String[] exampleFilePaths, String templateFilePath, String outputFilePath) throws Exception {
        System.out.println("Generating all-in-one configuration...");

        List<Configuration> combinedChildren = new ArrayList<>();
        int exampleIndex = 1;
        for (String exampleFilePath : exampleFilePaths) {
            System.out.println("Loading configuration from example file: " + exampleFilePath);
            TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
            Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
            Configuration treeWalkerModule = getTreeWalkerModule(xmlConfig);
            if (treeWalkerModule != null) {
                // Add an id property to each child module based on the example index
                for (Configuration child : treeWalkerModule.getChildren()) {
                    Configuration modifiedChild = addIdProperty(child, "example" + exampleIndex);
                    combinedChildren.add(modifiedChild);
                }
            }
            exampleIndex++;
        }

        // Build combined TreeWalker content
        String combinedModuleContent = buildCombinedTreeWalkerChildren(combinedChildren, "        ");

        // Read the template and replace the placeholder
        String template = new String(Files.readAllBytes(Path.of(templateFilePath)));
        String configContent = TemplateProcessor.replacePlaceholders(template, combinedModuleContent);

        // Write the final config content to the output file
        Files.write(Path.of(outputFilePath), configContent.getBytes());
    }

    private static Configuration addIdProperty(Configuration config, String idValue) {
        return new Configuration() {
            @Override
            public String getName() {
                return config.getName();
            }

            @Override
            public Map<String, String> getMessages() {
                return null;
            }

            @Override
            public String[] getAttributeNames() {
                return new String[0];
            }

            @Override
            public String getAttribute(String name) throws CheckstyleException {
                return null;
            }

            @Override
            public String[] getPropertyNames() {
                List<String> propertyNames = new ArrayList<>(Arrays.asList(config.getPropertyNames()));
                propertyNames.add("id");
                return propertyNames.toArray(new String[0]);
            }

            @Override
            public String getProperty(String name) throws CheckstyleException {
                if ("id".equals(name)) {
                    return idValue;
                }
                return config.getProperty(name);
            }

            @Override
            public Configuration[] getChildren() {
                return config.getChildren();
            }
        };
    }

    private static String buildCombinedTreeWalkerChildren(List<Configuration> children, String indent) {
        StringBuilder builder = new StringBuilder();

        for (Configuration child : children) {
            String childProperties = buildProperties(child, indent + "    ");
            if (!childProperties.isEmpty()) {
                builder.append(indent).append("<module name=\"").append(child.getName()).append("\">\n");
                builder.append(childProperties).append("\n");
                builder.append(indent).append("</module>\n\n"); // Added extra newline here
            } else {
                // Generate self-closing tag if there are no properties
                builder.append(indent).append("<module name=\"").append(child.getName()).append("\"/>\n\n"); // Added extra newline here
            }
        }

        return builder.toString().trim();
    }

    private static String buildProperties(Configuration config, String indent) {
        StringBuilder builder = new StringBuilder();
        boolean firstProperty = true;
        List<String> sortedPropertyNames = new ArrayList<>(Arrays.asList(config.getPropertyNames()));
        Collections.sort(sortedPropertyNames);
        for (String propertyName : sortedPropertyNames) {
            try {
                String propertyValue = config.getProperty(propertyName);
                if (!firstProperty) {
                    builder.append("\n");
                }
                builder.append(indent).append("<property name=\"").append(propertyName)
                        .append("\" value=\"").append(propertyValue).append("\"/>");
                firstProperty = false;
            } catch (CheckstyleException e) {
                System.err.println("Error retrieving property: " + e.getMessage());
            }
        }
        return builder.toString();
    }

    private static Configuration getTreeWalkerModule(Configuration config) {
        for (Configuration child : config.getChildren()) {
            if ("TreeWalker".equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    public static String sortProperties(String configContent) {
        String[] lines = configContent.split("\n");
        List<String> propertyLines = new ArrayList<>();
        StringBuilder sortedContent = new StringBuilder();

        for (String line : lines) {
            if (line.trim().startsWith("<property")) {
                propertyLines.add(line);
            } else {
                if (!propertyLines.isEmpty()) {
                    Collections.sort(propertyLines);
                    for (String propertyLine : propertyLines) {
                        sortedContent.append(propertyLine).append("\n");
                    }
                    propertyLines.clear();
                }
                sortedContent.append(line).append("\n");
            }
        }
        if (!propertyLines.isEmpty()) {
            Collections.sort(propertyLines);
            for (String propertyLine : propertyLines) {
                sortedContent.append(propertyLine).append("\n");
            }
        }
        return sortedContent.toString().trim();
    }

    private static String buildModuleContent(Configuration config, String indent) {
        StringBuilder builder = new StringBuilder();
        for (Configuration child : config.getChildren()) {
            String childProperties = buildProperties(child, indent + "    ");
            if (!childProperties.isEmpty()) {
                builder.append(indent).append("<module name=\"").append(child.getName()).append("\">\n");
                builder.append(childProperties).append("\n");
                builder.append(indent).append("</module>\n\n"); // Added extra newline here
            } else {
                // Generate self-closing tag if there are no properties
                builder.append(indent).append("<module name=\"").append(child.getName()).append("\"/>\n\n"); // Added extra newline here
            }
        }
        return builder.toString().trim();
    }
}