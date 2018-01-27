package org.gradle.samples

import org.gradle.internal.os.OperatingSystem
import org.gradle.samples.fixtures.Documentation
import org.gradle.samples.fixtures.NativeSample
import org.gradle.samples.fixtures.Samples
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assume
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ExecuteSamplesIntegrationTest extends Specification {
    @Shared
    Documentation documentation = new Documentation()

    @Unroll
    def "can build C++ '#sample.name'"() {
        given:
        sample.clean()
        runSetupFor(sample)

        expect:
        GradleRunner.create()
                .withProjectDir(sample.sampleDir)
                .withArguments("build")
                .build()

        GradleRunner.create()
                .withProjectDir(sample.sampleDir)
                .withArguments("xcode")
                .build()

        GradleRunner.create()
                .withProjectDir(sample.sampleDir)
                .withArguments("assembleRelease")
                .build()

        where:
        sample << Samples.getSamples("cpp")
    }

    @Requires({ !OperatingSystem.current().windows })
    @Unroll
    def "can build Swift '#sample.name'"() {
        // TODO - remove this once Swift 4 tools installed on Linux CI machines
        Assume.assumeTrue(!OperatingSystem.current().linux || sample.sampleName != 'swift-versions')

        given:
        sample.clean()
        runSetupFor(sample)

        expect:
        GradleRunner.create()
                .withProjectDir(sample.sampleDir)
                .withArguments("build")
                .build()

        GradleRunner.create()
                .withProjectDir(sample.sampleDir)
                .withArguments("xcode")
                .build()

        GradleRunner.create()
                .withProjectDir(sample.sampleDir)
                .withArguments("assembleRelease")
                .build()

        where:
        sample << Samples.getSamples("swift")
    }

    // TODO - replace this once Swift 4 tools installed on Linux CI machines
    @Requires({ OperatingSystem.current().linux })
    def "can build Swift 'swift-versions' with Swift 3 toolchain"() {
        given:
        def sample = Samples.useSampleIn("swift/swift-versions")
        sample.clean()
        runSetupFor(sample)

        expect:
        GradleRunner.create()
                .withProjectDir(sample.sampleDir)
                .withArguments("swift3-app:build")
                .build()
    }

    def runSetupFor(NativeSample sample) {
        documentation.getSample(sample.sampleName).setupSteps.each { command ->
            println "Running setup step " + command
            GradleRunner.create()
            .withProjectDir(sample.sampleDir)
            .withArguments(command.split().drop(1))
            .build()
        }
    }
}