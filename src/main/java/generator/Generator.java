package generator;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class Generator {

    private Project project;

    private String outputPath;

    public String lineSeparator = System.lineSeparator();

    public String packageStatement(String packageName) {
        return "package " + packageName + ";" + lineSeparator + lineSeparator;
    }

    public String importStatement(String importClassStr) {
        return "import " + importClassStr + ";" + lineSeparator;
    }

    public static String generateField(String modifier, String className, String fieldName) {
        return "\t" + modifier + " " + className + " " + fieldName + ";" + StringGen.ls;
    }

    public Generator(Project project, String outputPath) {
        this.project = project;
        this.outputPath = outputPath;
    }

    private String generateTestForClass(Class clazz, int numberOfTestsPerMethod) {

        String srcCode = getFileHeader(clazz);

        srcCode += getTestHeader(clazz);

        List<String> methodSourceCode = getTestMethods(clazz, numberOfTestsPerMethod);

        srcCode += String.join(lineSeparator, methodSourceCode);
        srcCode += "}" + lineSeparator;

        return srcCode;
    }

    @NotNull
    private List<String> getTestMethods(Class clazz, int numberOfTestsPerMethod) {

        String clazzSimpleName = clazz.getSimpleName();
        String testedClassFieldName = clazzSimpleName.substring(0, 1).toLowerCase() + clazzSimpleName.substring(1);

        List<String> methodSrcCodeList = new LinkedList<String>();

        TestMethodGen methodGen;
        for (Method m : clazz.getDeclaredMethods()) {

            for (int i = 0; i < numberOfTestsPerMethod; i++) {

                if (!Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                methodGen = new TestMethodGen(m, testedClassFieldName);
                String methodSrcCode = methodGen.gen(i);
                methodSrcCodeList.add(methodSrcCode);
            }
        }
        return methodSrcCodeList;
    }

    @NotNull
    private String getTestHeader(Class clazz) {
        String newCode = "public class " + clazz.getSimpleName() + "Test {" + StringGen.ls;
        newCode += generateField("private", clazz.getSimpleName(), clazz.getSimpleName().substring(0, 1).toLowerCase() + clazz.getSimpleName().substring(1)) + lineSeparator;
        TestBeforeMethodGen beforeGen = new TestBeforeMethodGen(clazz, clazz.getSimpleName().substring(0, 1).toLowerCase() + clazz.getSimpleName().substring(1));
        newCode += beforeGen.gen();
        newCode += lineSeparator;
        return newCode;
    }

    @NotNull
    private String getFileHeader(Class clazz) {
        String packageName = clazz.getCanonicalName().substring(0, clazz.getCanonicalName().lastIndexOf("."));
        String srcCode = packageStatement(packageName);
        srcCode += importStatement("org.junit.jupiter.api.Test");
        srcCode += importStatement("org.junit.jupiter.api.BeforeEach");
        srcCode += lineSeparator;
        return srcCode;
    }

    public void run(List<Class> classList, int numberOfTestsPerMethod) throws IOException {

        List<String> testStrings = classList.stream()
                .map((Class clazz) -> generateTestForClass(clazz,numberOfTestsPerMethod))
                .collect(toList());

        List<File> emptyTestFiles = classList.stream()
                .map(this::getOutputFilePath)
                .map(File::new)
                .collect(toList());

        writeTestsToFiles(testStrings, emptyTestFiles);
    }

    private void writeTestsToFiles(List<String> testStrings, List<File> emptyTestFiles) throws IOException {
        if (emptyTestFiles.size() != testStrings.size()){
            throw new IllegalArgumentException("test files list " +
                    "size not equal to the size of the list of test strings");
        }

        int averageListSize = (emptyTestFiles.size() + testStrings.size())/2;

        for (int i = 0; i < averageListSize; i++) {
            File file = emptyTestFiles.get(i);
            Path testFilePath = file.toPath();

            String test = testStrings.get(i);
            Files.deleteIfExists(testFilePath);
            Files.createDirectories(testFilePath.getParent());
            Files.createFile(testFilePath);
            Files.write(testFilePath, Arrays.asList(test.split("\n")));
        }
    }

    @NotNull
    private String getOutputFilePath(Class clazz) {
        String baseProjectDirectory = project.getBasePath();
        String localPathToTestFolder = outputPath;
        String testFile = clazz.getCanonicalName().replace(".", File.separator) + "Test.java";
        return baseProjectDirectory + File.separator + localPathToTestFolder + File.separator + testFile;
    }
}
