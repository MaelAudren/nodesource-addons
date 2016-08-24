package org.ow2.proactive.resourcemanager.nodesource.infrastructure;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeInformation;
import org.objectweb.proactive.core.runtime.ProActiveRuntime;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.NodeSource;
import org.python.google.common.collect.Sets;


public class AWSEC2InfrastructureTest {

    private AWSEC2Infrastructure awsec2Infrastructure;

    @Mock
    private ConnectorIaasController connectorIaasController;
    @Mock
    private NodeSource nodeSource;
    @Mock
    private Node node;
    @Mock
    private ProActiveRuntime proActiveRuntime;
    @Mock
    private NodeInformation nodeInformation;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        awsec2Infrastructure = new AWSEC2Infrastructure();

    }

    @Test
    public void testInitialParamateres() {
        assertThat(awsec2Infrastructure.aws_key, is(nullValue()));
        assertThat(awsec2Infrastructure.aws_secret_key, is(nullValue()));
        assertThat(awsec2Infrastructure.rmHostname, is(not(nullValue())));
        assertThat(awsec2Infrastructure.connectorIaasURL,
                is("http://" + awsec2Infrastructure.rmHostname + ":8080/connector-iaas"));
        assertThat(awsec2Infrastructure.image, is(nullValue()));
        assertThat(awsec2Infrastructure.numberOfInstances, is(1));
        assertThat(awsec2Infrastructure.numberOfNodesPerInstance, is(1));
        if (System.getProperty("os.name").contains("Windows")) {
            assertThat(awsec2Infrastructure.downloadCommand,
                    is("powershell -command \"& { (New-Object Net.WebClient).DownloadFile('" +
                        awsec2Infrastructure.rmHostname + ":8080/rest/node.jar', 'node.jar') }\""));
        } else {
            assertThat(awsec2Infrastructure.downloadCommand,
                    is("wget -nv " + awsec2Infrastructure.rmHostname + ":8080/rest/node.jar"));

        }
        assertThat(awsec2Infrastructure.additionalProperties, is(""));
        assertThat(awsec2Infrastructure.ram, is(512));
        assertThat(awsec2Infrastructure.cores, is(1));
    }

    @Test
    public void testConfigure() {

        when(nodeSource.getName()).thenReturn("Node source Name");
        awsec2Infrastructure.nodeSource = nodeSource;

        awsec2Infrastructure.configure("aws_key", "aws_secret_key", "test.activeeon.com",
                "http://localhost:8088/connector-iaas", "aws-image", "2", "3",
                "wget -nv test.activeeon.com/rest/node.jar", "-Dnew=value", 512, 1, "0.05");
    }

    @Test(expected = IllegalArgumentException.class)
    public void tesConfigureNotEnoughParameters() {

        when(nodeSource.getName()).thenReturn("Node source Name");
        awsec2Infrastructure.nodeSource = nodeSource;

        awsec2Infrastructure.configure("aws_key", "aws_secret_key", "test.activeeon.com",
                "http://localhost:8088/connector-iaas", "aws-image",
                "wget -nv test.activeeon.com/rest/node.jar", "-Dnew=value", 512, 1, "0.05");
    }

    @Test
    public void testAcquireNode() {

        when(nodeSource.getName()).thenReturn("Node source Name");
        awsec2Infrastructure.nodeSource = nodeSource;

        awsec2Infrastructure.configure("aws_key", "aws_secret_key", "test.activeeon.com",
                "http://localhost:8088/connector-iaas", "aws-image", "2", "3",
                "wget -nv test.activeeon.com/rest/node.jar", "-Dnew=value", 512, 1, "0.05");

        awsec2Infrastructure.connectorIaasController = connectorIaasController;
        awsec2Infrastructure.nodeSource = nodeSource;
        awsec2Infrastructure.rmUrl = "http://test.activeeon.com";

        when(connectorIaasController.createInfrastructure("node_source_name", "aws_key", "aws_secret_key",
                null, true)).thenReturn("node_source_name");

        when(connectorIaasController.createInstancesWithSpotPrice("node_source_name", "node_source_name",
                "aws-image", 2, 1, 512, "0.05")).thenReturn(Sets.newHashSet("123", "456"));

        awsec2Infrastructure.acquireNode();

        verify(connectorIaasController, times(1)).waitForConnectorIaasToBeUP();

        verify(connectorIaasController).createInfrastructure("node_source_name", "aws_key", "aws_secret_key",
                null, true);

        verify(connectorIaasController).createInstancesWithSpotPrice("node_source_name", "node_source_name",
                "aws-image", 2, 1, 512, "0.05");

        verify(connectorIaasController, times(2)).executeScript(anyString(), anyString(), anyList());

    }

    @Test
    public void testAcquireAllNodes() {
        testAcquireNode();
    }

    @Test
    public void testRemoveNode() throws ProActiveException, RMException {

        when(nodeSource.getName()).thenReturn("Node source Name");
        awsec2Infrastructure.nodeSource = nodeSource;

        awsec2Infrastructure.configure("aws_key", "aws_secret_key", "test.activeeon.com",
                "http://localhost:8088/connector-iaas", "aws-image", "2", "3",
                "wget -nv test.activeeon.com/rest/node.jar", "-Dnew=value", 512, 1, "0.05");

        awsec2Infrastructure.connectorIaasController = connectorIaasController;

        when(node.getProperty(AWSEC2Infrastructure.INSTANCE_ID_NODE_PROPERTY)).thenReturn("123");

        when(node.getNodeInformation()).thenReturn(nodeInformation);

        when(node.getProActiveRuntime()).thenReturn(proActiveRuntime);

        when(nodeInformation.getName()).thenReturn("nodename");

        awsec2Infrastructure.nodesPerInstances.put("123", Sets.newHashSet("nodename"));

        awsec2Infrastructure.removeNode(node);

        verify(proActiveRuntime).killNode("nodename");

        verify(connectorIaasController).terminateInstance("node_source_name", "123");

        assertThat(awsec2Infrastructure.nodesPerInstances.isEmpty(), is(true));

    }

    @Test
    public void testNotifyAcquiredNode() throws ProActiveException, RMException {

        when(nodeSource.getName()).thenReturn("Node source Name");
        awsec2Infrastructure.nodeSource = nodeSource;

        awsec2Infrastructure.configure("aws_key", "aws_secret_key", "test.activeeon.com",
                "http://localhost:8088/connector-iaas", "aws-image", "2", "3",
                "wget -nv test.activeeon.com/rest/node.jar", "-Dnew=value", 512, 1, "0.05");

        awsec2Infrastructure.connectorIaasController = connectorIaasController;

        when(node.getProperty(AWSEC2Infrastructure.INSTANCE_ID_NODE_PROPERTY)).thenReturn("123");

        when(node.getNodeInformation()).thenReturn(nodeInformation);

        when(nodeInformation.getName()).thenReturn("nodename");

        awsec2Infrastructure.notifyAcquiredNode(node);

        assertThat(awsec2Infrastructure.nodesPerInstances.get("123").isEmpty(), is(false));
        assertThat(awsec2Infrastructure.nodesPerInstances.get("123").size(), is(1));
        assertThat(awsec2Infrastructure.nodesPerInstances.get("123").contains("nodename"), is(true));

    }

    @Test
    public void testGetDescription() {
        assertThat(awsec2Infrastructure.getDescription(),
                is("Handles nodes from the Amazon Elastic Compute Cloud Service."));
    }

}
