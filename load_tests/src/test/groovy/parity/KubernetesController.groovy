package parity

import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.ConfigMapBuilder
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.ContainerPort
import io.fabric8.kubernetes.api.model.ContainerPortBuilder
import io.fabric8.kubernetes.api.model.KeyToPath
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceBuilder
import io.fabric8.kubernetes.api.model.PodSpec
import io.fabric8.kubernetes.api.model.PodSpecBuilder
import io.fabric8.kubernetes.api.model.PodTemplateSpec
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServiceBuilder
import io.fabric8.kubernetes.api.model.ServicePort
import io.fabric8.kubernetes.api.model.ServicePortBuilder
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VolumeBuilder
import io.fabric8.kubernetes.api.model.VolumeMount
import io.fabric8.kubernetes.api.model.VolumeMountBuilder
import io.fabric8.kubernetes.api.model.extensions.Deployment
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpecBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.handlers.ConfigMapHandler
import io.fabric8.kubernetes.client.handlers.DeploymentHandler

import static groovy.io.FileType.FILES

class KubernetesController {

    static Service launchParityNetwork(workingDirPath, String jsonRpcThreads, Integer nodes, LinkedHashMap<String,
            String> labels, String namespace, KubernetesClient kubernetesClient) {
        String parityconfigName = 'parityconfig'
        String deploymentName = "parity-benchmark-deployment"
        String networkName = "aura_test"
        String configVolumeName = "$parityconfigName-volume"
        String parityConfigRoot = "${workingDirPath}/deployment"
        File chaindir = new File("${parityConfigRoot}/chain/")
        File secretsdir = new File("${parityConfigRoot}/1/")
        File networkdir = new File("$parityConfigRoot/1/$networkName")

        List<String> args = [
                '--config', '/parity/authority.edited.toml',
                '--chain', '/parity/spec.json',
                '--reserved-peers', '/parity/reserved_peers',
                "--jsonrpc-server-threads $jsonRpcThreads" as String
        ]

        List ports = [8180, 8545, 8546, 30303]
        LinkedHashMap configs = [:]

        secretsdir.eachFile(FILES) {
            configs.put(it.name, it.text)
        }
        chaindir.eachFile(FILES) {
            configs.put(it.name, it.text)
        }
        networkdir.eachFile(FILES) {
            configs.put(it.name, it.text)
        }

        createNameSpaceIfNotExist(namespace, kubernetesClient)

        ConfigMap networkConfig = removeExistingConfig(configs, parityconfigName, namespace, kubernetesClient)

        removeExistingDeployment(deploymentName, namespace, kubernetesClient, labels)

        kubernetesClient.configMaps().withName(parityconfigName).createOrReplace(networkConfig)

        Volume volume = buildVolume(configs, parityconfigName, configVolumeName)
        ArrayList<VolumeMount> volumeMountList = buildVolumeMountList(configs, parityconfigName, configVolumeName)

        DeploymentSpec deploymentSpec = buildNewDeploymentSpec(args, generateContainerPortList(ports), volumeMountList, volume, nodes, labels)

        Deployment newDeployment = new DeploymentBuilder()
                .withSpec(deploymentSpec)
                .withNewMetadata()
                .withName(deploymentName)
                .withLabels(labels)
                .withNamespace(namespace)
                .and()
                .build();

        kubernetesClient.extensions().deployments().createOrReplace(newDeployment)

        def existingService = kubernetesClient.services().inNamespace(namespace).withName(namespace).get()
        if (!existingService) {
            def service = createService(ports, labels, namespace, kubernetesClient)
        } else {
            return kubernetesClient.services().inNamespace(namespace).withName(namespace).get()
        }
//        return createService(ports, labels, namespace, kubernetesClient)
    }

    public
    static Service createService(List<Integer> ports, LinkedHashMap<String, String> labels, String namespace, DefaultKubernetesClient kubernetesClient) {
        List servicePorts = generateServicePortList(ports)

        Service parityservice = new ServiceBuilder()
                .withNewSpec().withType('LoadBalancer')
                .withSelector(labels).withSelector(labels).withPorts(servicePorts)
                .and().withNewMetadata().withName('parity-benchmark-service').withNamespace(namespace).withLabels(labels)
                .and().build()

        kubernetesClient.services().delete(parityservice)
        return kubernetesClient.services().create(parityservice)
    }

    static List<ContainerPort> generateContainerPortList(List<Integer> ports) {
        def containerPortBuilder = new ContainerPortBuilder()

        List<ContainerPort> containerPortsList = []

        ports.each {
            containerPortsList.add(containerPortBuilder.withContainerPort(it).build())
        }

        containerPortsList
    }

    static List<ContainerPort> generateServicePortList(List<Integer> ports) {
        def servicePortBuilder = new ServicePortBuilder()

        List<ServicePort> servicePorts = []

        ports.each {
            servicePorts.add(servicePortBuilder
                    .withPort(it).withName(it as String)
                    .build())
        }

        servicePorts
    }


    static void createNameSpaceIfNotExist(String namespace, DefaultKubernetesClient client) {
        Namespace ns1 = new NamespaceBuilder().withNewMetadata().withName(namespace).and().build();
        try {
            client.namespaces().withName(namespace).createOrReplace(ns1);
        } catch (KubernetesClientException e) {
            println e.message
        }
    }

    static void removeExistingDeployment(String deploymentName, String namespace, DefaultKubernetesClient client, labels) {
        DeploymentHandler deploymentHandler = new DeploymentHandler()
        Deployment deploymentDel = new DeploymentBuilder()
                .withNewMetadata()
                .withName(deploymentName)
                .withLabels(labels)
                .withNamespace(namespace)
                .and()
                .build();

        deploymentHandler.delete(client.httpClient, client.configuration, namespace, deploymentDel)
    }

    static DeploymentSpec buildNewDeploymentSpec(List<String> args, List<ContainerPort> containerPortList,
                                                 ArrayList<VolumeMount> volumeMountList, Volume volume,
                                                 int nodes, LinkedHashMap<String, String> labels) {
        Container container = new ContainerBuilder()
                .withName('parity-benchmark-pod')
                .withImage('parity/parity:nightly')
                .withImagePullPolicy('Always')
                .withArgs(args)
                .withPorts(containerPortList)
                .withVolumeMounts(volumeMountList)
                .build()

        PodSpec podSpec = new PodSpecBuilder()
                .withContainers(container)
                .withVolumes(volume)
                .build()

        PodTemplateSpec podTemplateSpec = new PodTemplateSpecBuilder()
                .withNewMetadata().withLabels(labels)
                .and()
                .withSpec(podSpec)
                .build()

        DeploymentSpec deploymentSpec = new DeploymentSpecBuilder()
                .withTemplate(podTemplateSpec).withReplicas(nodes)
                .withNewSelector().withMatchLabels(labels)
                .and()
                .build()
        deploymentSpec
    }

    static Volume buildVolume(LinkedHashMap<String, String> configs, parityconfigName, String volumName) {
        ConfigMapVolumeSource configMapVolumeSource = generateConfigMapVolumeSource(configs, parityconfigName)
        new VolumeBuilder()
                .withName(volumName)
                .withConfigMap(configMapVolumeSource)
                .build()

    }

    static ConfigMapVolumeSource generateConfigMapVolumeSource(LinkedHashMap<String, String> configs, parityconfigName) {
        List<KeyToPath> keyToPathArrayList = []

        configs.each { Map.Entry<Object, String> entry ->
            keyToPathArrayList.add(new KeyToPath(entry.key, 0777, entry.key))
        }

        ConfigMapVolumeSource configMapVolumeSource = new ConfigMapVolumeSourceBuilder()
                .withName(parityconfigName)
                .withItems(keyToPathArrayList)
                .build()
        configMapVolumeSource
    }

    static ArrayList<VolumeMount> buildVolumeMountList(LinkedHashMap<String, String> configs, String configVolumeName, String volumeMountName) {
        List<VolumeMount> volumeMountList = new ArrayList<VolumeMount>()

        generateConfigMapVolumeSource(configs, configVolumeName).items.each {
            volumeMountList.add(new VolumeMountBuilder()
                    .withName(volumeMountName)
                    .withMountPath("/parity/$it.path")
                    .withSubPath(it.path)
                    .withReadOnly(true)
                    .build())
        }

        volumeMountList
    }

    static ConfigMap removeExistingConfig(LinkedHashMap<String, String> configs, String parityconfigName, String namespace, DefaultKubernetesClient client) {
        ConfigMapHandler configMapHandler = new ConfigMapHandler()

        ConfigMap networkConfig = new ConfigMapBuilder().withData(configs)
                .withNewMetadata()
                .withName(parityconfigName)
                .withNamespace(namespace)
                .and().build()

        configMapHandler.delete(client.httpClient, client.configuration, namespace, networkConfig)
        networkConfig
    }

}
