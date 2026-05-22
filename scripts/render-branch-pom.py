#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
按 agent-invoker-java-sdk 分支名写入对应的 pom.xml。

用法: python3 scripts/render-branch-pom.py <branch>
"""
from __future__ import annotations

import os
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
POM = ROOT / "pom.xml"

ALIYUN_DM = """
    <distributionManagement>
        <repository>
            <id>2624322-release-6F6h6R</id>
            <url>https://packages.aliyun.com/6927b116e6c3e0425dbdf60d/maven/2624322-release-6f6h6r</url>
            <releases>
                <enabled>true</enabled>
            </releases>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
        <snapshotRepository>
            <id>2624322-snapshot-3EoOv3</id>
            <url>https://packages.aliyun.com/6927b116e6c3e0425dbdf60d/maven/2624322-snapshot-3eoov3</url>
            <releases>
                <enabled>false</enabled>
            </releases>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
            <uniqueVersion>true</uniqueVersion>
        </snapshotRepository>
    </distributionManagement>
"""

_ANY_DM = re.compile(
    r"\s*<distributionManagement>.*?</distributionManagement>",
    re.DOTALL,
)

COMMON_META = """    <licenses>
        <license>
            <name>The Apache Software License, Version 2.0</name>
            <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
    </licenses>

    <scm>
        <connection>scm:git:https://github.com/hiwepy/${project.artifactId}.git</connection>
        <developerConnection>scm:git:https://github.com/hiwepy/${project.artifactId}.git</developerConnection>
        <url>https://github.com/hiwepy/${project.artifactId}</url>
        <tag>${project.artifactId}</tag>
    </scm>

    <developers>
        <developer>
            <name>hiwepy</name>
            <email>hiwepy@gmail.com</email>
            <roles>
                <role>developer</role>
            </roles>
            <timezone>+8</timezone>
        </developer>
    </developers>
"""

COMMON_DEPS_J17 = """        <jackson.version>2.17.2</jackson.version>
        <junit.version>5.11.4</junit.version>
        <lombok.version>1.18.36</lombok.version>"""

COMMON_DEPS_J8 = """        <jackson.version>2.13.5</jackson.version>
        <junit.version>5.8.2</junit.version>
        <lombok.version>1.18.30</lombok.version>"""

DEPS_BLOCK_TEMPLATE = """
    <dependencies>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>${slf4j.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.hiwepy</groupId>
            <artifactId>openclaw-java-sdk</artifactId>
            <version>${openclaw-java-sdk.version}</version>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>${slf4j.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
"""


def write_slim_j17(version: str, slf4j: str, description_suffix: str) -> None:
    body = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.github.hiwepy</groupId>
    <artifactId>agent-invoker-java-sdk</artifactId>
    <version>{version}</version>
    <packaging>jar</packaging>
    <name>${{project.groupId}}:${{project.artifactId}}</name>
    <description>AI Agent Invoker abstraction SDK — {description_suffix}</description>
    <url>https://github.com/hiwepy/${{project.artifactId}}</url>

{COMMON_META}
    <properties>
        <java.version>17</java.version>
        <maven.compiler.release>${{java.version}}</maven.compiler.release>
        <maven.compiler.source>${{java.version}}</maven.compiler.source>
        <maven.compiler.target>${{java.version}}</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <slf4j.version>{slf4j}</slf4j.version>
{COMMON_DEPS_J17}
        <openclaw-java-sdk.version>{version}</openclaw-java-sdk.version>

        <maven-compiler-plugin.version>3.13.0</maven-compiler-plugin.version>
        <maven-surefire-plugin.version>3.5.2</maven-surefire-plugin.version>
        <maven-install-plugin.version>2.5.2</maven-install-plugin.version>
        <maven-deploy-plugin.version>2.8.2</maven-deploy-plugin.version>
        <maven-release-plugin.version>2.5.3</maven-release-plugin.version>
        <maven-source-plugin.version>3.0.1</maven-source-plugin.version>
        <maven-javadoc-plugin.version>3.0.1</maven-javadoc-plugin.version>
        <maven-jar-plugin.version>3.1.1</maven-jar-plugin.version>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>${{maven-compiler-plugin.version}}</version>
                <configuration>
                    <release>${{java.version}}</release>
                    <encoding>${{project.build.sourceEncoding}}</encoding>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${{maven-surefire-plugin.version}}</version>
            </plugin>
        </plugins>
    </build>

{DEPS_BLOCK_TEMPLATE}
</project>
'''
    POM.write_text(body, encoding="utf-8")


def write_minimal_j8(version: str) -> None:
    body = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.github.hiwepy</groupId>
    <artifactId>agent-invoker-java-sdk</artifactId>
    <version>{version}</version>
    <packaging>jar</packaging>
    <name>${{project.groupId}}:${{project.artifactId}}</name>
    <description>AI Agent Invoker abstraction SDK — Spring Boot 2.3.x line (JDK 8)</description>
    <url>https://github.com/hiwepy/${{project.artifactId}}</url>

{COMMON_META}
    <properties>
        <java.version>11</java.version>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <slf4j.version>1.7.36</slf4j.version>
{COMMON_DEPS_J8}
        <openclaw-java-sdk.version>{version}</openclaw-java-sdk.version>

        <maven-compiler-plugin.version>3.8.1</maven-compiler-plugin.version>
        <maven-surefire-plugin.version>2.22.2</maven-surefire-plugin.version>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>${{maven-compiler-plugin.version}}</version>
                <configuration>
                    <source>${{maven.compiler.source}}</source>
                    <target>${{maven.compiler.target}}</target>
                    <encoding>${{project.build.sourceEncoding}}</encoding>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${{maven-surefire-plugin.version}}</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-deploy-plugin</artifactId>
                <version>3.1.2</version>
            </plugin>
        </plugins>
    </build>

{DEPS_BLOCK_TEMPLATE}
</project>
'''
    POM.write_text(body, encoding="utf-8")


def write_full_j8_27(version: str) -> None:
    """对齐 openclaw-java-sdk 2.7.x 插件矩阵，JDK 8。"""
    body = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.github.hiwepy</groupId>
    <artifactId>agent-invoker-java-sdk</artifactId>
    <version>{version}</version>
    <packaging>jar</packaging>
    <name>${{project.groupId}}:${{project.artifactId}}</name>
    <description>AI Agent Invoker abstraction SDK — Spring Boot 2.7.x line (JDK 11)</description>
    <url>https://github.com/hiwepy/${{project.artifactId}}</url>

{COMMON_META}
    <distributionManagement>
        <snapshotRepository>
            <id>central-snapshots</id>
            <name>Central Portal Snapshots</name>
            <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        </snapshotRepository>
    </distributionManagement>

    <properties>
        <java.version>1.8</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <slf4j.version>1.7.36</slf4j.version>
{COMMON_DEPS_J8}
        <openclaw-java-sdk.version>{version}</openclaw-java-sdk.version>

        <maven.version>3.0</maven.version>
        <maven-clean-plugin.version>3.1.0</maven-clean-plugin.version>
        <maven-compiler-plugin.version>3.8.1</maven-compiler-plugin.version>
        <maven-deploy-plugin.version>2.8.2</maven-deploy-plugin.version>
        <maven-enforcer-plugin.version>3.0.0-M2</maven-enforcer-plugin.version>
        <maven-gpg-plugin.version>1.6</maven-gpg-plugin.version>
        <maven-install-plugin.version>3.0.0-M1</maven-install-plugin.version>
        <maven-jar-plugin.version>3.1.1</maven-jar-plugin.version>
        <maven-javadoc-plugin.version>3.2.1</maven-javadoc-plugin.version>
        <maven-release-plugin.version>2.5.3</maven-release-plugin.version>
        <maven-resources-plugin.version>3.1.0</maven-resources-plugin.version>
        <maven-surefire-plugin.version>3.0.0-M7</maven-surefire-plugin.version>
        <maven-source-plugin.version>3.2.1</maven-source-plugin.version>
        <maven-central-publishing-plugin.version>0.10.0</maven-central-publishing-plugin.version>
    </properties>

    <build>
        <pluginManagement>
            <plugins>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-clean-plugin</artifactId><version>${{maven-clean-plugin.version}}</version></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>${{maven-compiler-plugin.version}}</version><configuration><source>${{java.version}}</source><target>${{java.version}}</target><encoding>${{project.build.sourceEncoding}}</encoding><maxmem>512M</maxmem></configuration></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-deploy-plugin</artifactId><version>${{maven-deploy-plugin.version}}</version></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-enforcer-plugin</artifactId><version>${{maven-enforcer-plugin.version}}</version><executions><execution><id>default-cli</id><goals><goal>enforce</goal></goals><phase>validate</phase><configuration><rules><requireMavenVersion><message>You are running an older version of Maven.</message><version>[${{maven.version}}.0,)</version></requireMavenVersion><requireJavaVersion><message>You are running an older version of Java.</message><version>[${{java.version}},)</version></requireJavaVersion></rules></configuration></execution></executions></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-gpg-plugin</artifactId><version>${{maven-gpg-plugin.version}}</version><executions><execution><id>sign-artifacts</id><phase>verify</phase><goals><goal>sign</goal></goals></execution></executions></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-install-plugin</artifactId><version>${{maven-install-plugin.version}}</version></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-resources-plugin</artifactId><version>${{maven-resources-plugin.version}}</version><configuration><encoding>${{project.build.sourceEncoding}}</encoding></configuration></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-release-plugin</artifactId><version>${{maven-release-plugin.version}}</version><configuration><tagNameFormat>v@${{project.version}}</tagNameFormat><autoVersionSubmodules>true</autoVersionSubmodules><useReleaseProfile>false</useReleaseProfile><releaseProfiles>release</releaseProfiles><goals>deploy</goals></configuration></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-source-plugin</artifactId><version>${{maven-source-plugin.version}}</version><configuration><attach>true</attach></configuration><executions><execution><id>attach-sources</id><goals><goal>jar-no-fork</goal></goals></execution></executions></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>${{maven-surefire-plugin.version}}</version><configuration><skip>true</skip><skipTests>true</skipTests></configuration></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-jar-plugin</artifactId><version>${{maven-jar-plugin.version}}</version><configuration><skipIfEmpty>true</skipIfEmpty></configuration></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-javadoc-plugin</artifactId><version>${{maven-javadoc-plugin.version}}</version><configuration><failOnError>false</failOnError></configuration><executions><execution><id>attach-javadocs</id><phase>package</phase><goals><goal>jar</goal></goals></execution></executions></plugin>
                <plugin><groupId>org.sonatype.central</groupId><artifactId>central-publishing-maven-plugin</artifactId><version>${{maven-central-publishing-plugin.version}}</version><extensions>true</extensions><configuration><publishingServerId>central</publishingServerId><autoPublish>true</autoPublish><waitUntil>published</waitUntil></configuration></plugin>
            </plugins>
        </pluginManagement>
        <plugins>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-enforcer-plugin</artifactId></plugin>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId></plugin>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId></plugin>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-jar-plugin</artifactId></plugin>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-source-plugin</artifactId></plugin>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-install-plugin</artifactId></plugin>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-deploy-plugin</artifactId></plugin>
        </plugins>
    </build>

{DEPS_BLOCK_TEMPLATE}
</project>
'''
    POM.write_text(body, encoding="utf-8")


def apply_aliyun_distribution_management() -> None:
    text = POM.read_text(encoding="utf-8")
    stripped = _ANY_DM.sub("", text)
    marker = "</developers>"
    if marker not in stripped:
        raise SystemExit("pom.xml: </developers> not found, cannot inject distributionManagement")
    pos = stripped.find(marker) + len(marker)
    POM.write_text(stripped[:pos] + "\n" + ALIYUN_DM + "\n" + stripped[pos:], encoding="utf-8")


SNAPSHOT_SUFFIX = f"{os.environ.get('RELEASE_DATE', '20260520')}-SNAPSHOT"


def render(branch: str) -> None:
    snapshot = SNAPSHOT_SUFFIX
    if branch == "main":
        write_slim_j17(f"3.3.x.{snapshot}", "2.0.16", "main / Spring Boot 3.3.x baseline (JDK 17)")
    elif branch == "2.3.x":
        write_minimal_j8(f"2.3.x.{snapshot}")
    elif branch == "2.7.x":
        write_full_j8_27(f"2.7.x.{snapshot}")
    elif branch in {"3.0.x", "3.1.x", "3.2.x", "3.3.x", "3.4.x"}:
        write_slim_j17(f"{branch}.{snapshot}", "1.7.36", f"Spring Boot {branch[:-4]} line (JDK 17)")
    elif branch in {"3.5.x", "4.0.x"}:
        write_slim_j17(f"{branch}.{snapshot}", "2.0.16", f"Spring Boot {branch[:-4]} line (JDK 17, SLF4J 2.x)")
    else:
        raise SystemExit(f"unsupported branch: {branch}")
    apply_aliyun_distribution_management()


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__.strip(), file=sys.stderr)
        sys.exit(2)
    render(sys.argv[1])
