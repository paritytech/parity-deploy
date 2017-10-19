package parity

class DockerController {

    static def runParity(File workingDir) {
        "docker-compose up ".execute null, workingDir
    }

    static def bringDownParity(File workingDir) {
        "docker-compose down".execute null, workingDir
    }


}
