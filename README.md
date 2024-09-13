---                                                       new test 1 2 3
description: Use this documentation to get started with the JetBrains plugin.
---

# JetBrains plugin

## **Scan early, fix as you develop: elevate your security posture**

Integrating security checks early in your development lifecycle helps you pass security reviews seamlessly and avoid expensive fixes down the line.

The Snyk JetBrains plugin allows you to analyze your code, open-source dependencies, Docker images, and Infrastructure as Code (IaC) configurations. With actionable insights directly in your IDE, you can address issues as they arise.

**Key features:**

* **In-line issue highlighting:** Security issues are flagged directly within your code, categorized by type and severity for quick identification and resolution.
* **Comprehensive scanning:** The extension scans for a wide range of security issues, including:
  * [**Open Source Security**](https://snyk.io/product/open-source-security-management/)**:** Detects vulnerabilities and license issues in both direct and transitive open-source dependencies. Automated fix suggestions simplify remediation. Explore more in the [Snyk Open Source documentation](https://docs.snyk.io/scan-using-snyk/snyk-open-source).
  * [**Code Security**](https://snyk.io/product/snyk-code/)**:** Identifies security vulnerabilities in your custom code. Explore more in the [Snyk Code documentation](https://docs.snyk.io/scan-using-snyk/snyk-code).
  * [**IaC Security**](https://snyk.io/product/infrastructure-as-code-security/)**:** Uncovers configuration issues in your Infrastructure as Code templates (Terraform, Kubernetes, CloudFormation, Azure Resource Manager). Explore more in the [IaC documentation](https://docs.snyk.io/scan-using-snyk/snyk-iac).
  * [**Container Security**](https://snyk.io/product/container-vulnerability-management/): Finds security vulnerabilities in your base images; supports all the [operating system distributions supported by Snyk Container](https://docs.snyk.io/scan-using-snyk/snyk-container/how-snyk-container-works/operating-system-distributions-supported-by-snyk-container). See also the [Snyk Container](https://docs.snyk.io/scan-using-snyk/snyk-container) docs.
* **Broad language and framework support:** Snyk Open Source and Snyk Code cover a wide array of package managers, programming languages, and frameworks, with ongoing updates to support the latest technologies. For the most up-to-date information on supported languages, package managers, and frameworks, see the [supported language technologies pages](https://docs.snyk.io/supported-languages-package-managers-and-frameworks).

## How to install and set up the extension


The latest Snyk JetBrains plugin is supported by all JetBrains IDEs 2023.3 or newer.

An older plugin version is supported by JetBrains IDEs 2020.3 or newer.


You can use the Snyk JetBrains plugin in the following environments:

* Linux: 386, AMD64, and ARM64
* Linux Alpine: 386 and AMD64
* Windows: 386, AMD64, and ARM64
* MacOS: AMD64 and ARM64

Install the plugin at any time free of charge from the [JetBrains marketplace](https://plugins.jetbrains.com/plugin/10972-snyk-vulnerability-scanner) and use it with any Snyk account, including the Free plan. For more information, see the [IDEA plugin installation guide](https://www.jetbrains.com/help/idea/managing-plugins.html).

When the extension is installed, it automatically downloads the  [Snyk CLI,](https://docs.snyk.io/snyk-cli) which includes the [Language Server](https://docs.snyk.io/scm-ide-and-ci-cd-integrations/snyk-ide-plugins-and-extensions/snyk-language-server).

Continue by following the instructions in the other JetBrains plugin docs:

* [Configuration, environment variables, and proxy for the JetBrains plugins](https://docs.snyk.io/scm-ide-and-ci-cd-integrations/snyk-ide-plugins-and-extensions/jetbrains-plugins/configuration-environment-variables-and-proxy-for-the-jetbrains-plugins)
* [JetBrains plugin authentication](https://docs.snyk.io/scm-ide-and-ci-cd-integrations/snyk-ide-plugins-and-extensions/jetbrains-plugins/authentication-for-the-jetbrains-plugins)
* [JetBrains plugin folder trust](https://docs.snyk.io/scm-ide-and-ci-cd-integrations/snyk-ide-plugins-and-extensions/jetbrains-plugins/jetbrains-plugin-folder-trust)
* [Run an analysis with the JetBrains plugins](https://docs.snyk.io/scm-ide-and-ci-cd-integrations/snyk-ide-plugins-and-extensions/jetbrains-plugins/run-an-analysis-with-the-jetbrains-plugins)

## Support

For troubleshooting and known issues, see [Troubleshooting for the JetBrains plugin](https://docs.snyk.io/scm-ide-and-ci-cd-integrations/snyk-ide-plugins-and-extensions/jetbrains-plugins/troubleshooting-for-the-jetbrains-plugin).

If you need help, submit a [request](https://support.snyk.io/hc/en-us/requests/new) to Snyk Support.
