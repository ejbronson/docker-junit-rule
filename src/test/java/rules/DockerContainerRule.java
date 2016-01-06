package rules;

import com.spotify.docker.client.*;
import com.spotify.docker.client.messages.*;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapted from https://gist.github.com/mosheeshel/c427b43c36b256731a0b
 */
public class DockerContainerRule extends ExternalResource {
    public static final String DOCKER_SERVICE_URL = "http://192.168.99.100:2375";

    private final DockerClient dockerClient;
    private ContainerCreation container;
    private Map<String, List<PortBinding>> ports;

    public DockerContainerRule(String imageName) {
        this(imageName, new String[0], null);
    }

    public DockerContainerRule(String imageName, String[] ports) {
        this(imageName, ports, null);
    }

    public DockerContainerRule(String imageName, String[] ports, String cmd) {

//        dockerClient = new DefaultDockerClient(dockerServiceUrl);
        DockerCertificates dockerCertificates;
        try {
            String userHome = System.getProperty("user.home");
            dockerCertificates = new DockerCertificates(Paths.get(userHome, ".docker/machine/certs"));
        } catch (DockerCertificateException e) {
            throw new IllegalStateException(e);
        }
        dockerClient = DefaultDockerClient.builder()
                .uri(URI.create("https://192.168.99.100:2376"))
                .dockerCertificates(dockerCertificates)
                .build();

        Map<String, List<PortBinding>> portBindings = new HashMap<>();
        for (String port : ports) {
            List<PortBinding> hostPorts = Collections.singletonList(PortBinding.randomPort("0.0.0.0"));
            portBindings.put(port, hostPorts);
        }

        HostConfig hostConfig = HostConfig.builder()
                .portBindings(portBindings)
                .build();

        ContainerConfig.Builder configBuilder = ContainerConfig.builder()
                .hostConfig(hostConfig)
                .image(imageName)
                .networkDisabled(false)
                .exposedPorts(ports);

        if (cmd != null) {
            configBuilder = configBuilder.cmd(cmd);
        }
        ContainerConfig containerConfig = configBuilder.build();


        try {
            dockerClient.pull(imageName);
            container = dockerClient.createContainer(containerConfig);
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return super.apply(base, description);
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        dockerClient.startContainer(container.id());
        ContainerInfo info = dockerClient.inspectContainer(container.id());
        ports = info.networkSettings().ports();
    }

    public int getHostPort(String containerPort) {
        List<PortBinding> portBindings = ports.get(containerPort);
        if (portBindings.isEmpty()) {
            return -1;
        }
        return Integer.parseInt(portBindings.get(0).hostPort());
    }

    public String getDockerHost() {
        return dockerClient.getHost();
    }

    @Override
    protected void after() {
        super.after();
        try {
            dockerClient.killContainer(container.id());
            dockerClient.removeContainer(container.id(), true);
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}