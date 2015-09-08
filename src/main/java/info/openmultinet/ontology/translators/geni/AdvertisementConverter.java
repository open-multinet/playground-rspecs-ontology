package info.openmultinet.ontology.translators.geni;

import info.openmultinet.ontology.exceptions.InvalidModelException;
import info.openmultinet.ontology.exceptions.MissingRspecElementException;
import info.openmultinet.ontology.translators.AbstractConverter;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.ActionSpec;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.Available;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.AvailableContents;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.Cloud;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.DiskImageContents;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.ExternalReferenceContents;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.Fd;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.HardwareTypeContents;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.HopContent;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.LinkContents;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.LocationContents;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.Monitoring;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.NextHopContent;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.NodeContents;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.NodeContents.SliverType;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.NodeContents.SliverType.DiskImage;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.NodeType;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.ObjectFactory;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.PathContent;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.Pc;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.RSpecContents;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.RspecOpstate;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.RspecSharedVlan;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.RspecTypeContents;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.StateSpec;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.StitchContent;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.WaitSpec;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.InterfaceContents;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.ComponentManager;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.InterfaceRefContents;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.LinkPropertyContents;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.LinkType;
import info.openmultinet.ontology.vocabulary.Geo;
import info.openmultinet.ontology.vocabulary.Omn;
import info.openmultinet.ontology.vocabulary.Omn_domain_pc;
import info.openmultinet.ontology.vocabulary.Omn_federation;
import info.openmultinet.ontology.vocabulary.Omn_lifecycle;
import info.openmultinet.ontology.vocabulary.Omn_resource;
import info.openmultinet.ontology.vocabulary.Omn_service;

import java.io.InputStream;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.xerces.dom.ElementNSImpl;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.shared.InvalidPropertyURIException;
import com.hp.hpl.jena.shared.PropertyNotFoundException;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

public class AdvertisementConverter extends AbstractConverter {

	private static final String JAXB = "info.openmultinet.ontology.translators.geni.jaxb.advertisement";
	private static final String PREFIX = "http://open-multinet.info/omnlib/converter";
	private static final Logger LOG = Logger
			.getLogger(AdvertisementConverter.class.getName());
	private Model model;
	private JAXBContext context;
	private Unmarshaller unmarshaller;
	private ObjectFactory of;
	private XMLInputFactory xmlif;

	public AdvertisementConverter() throws JAXBException {
		super();
		this.model = ModelFactory.createDefaultModel();
		this.context = JAXBContext.newInstance(RSpecContents.class);
		this.unmarshaller = context.createUnmarshaller();
		this.of = new ObjectFactory();
		this.xmlif = XMLInputFactory.newInstance();
	}

	public Model getModel(final InputStream input) throws JAXBException,
			InvalidModelException, XMLStreamException,
			MissingRspecElementException {

		final RSpecContents rspecAdvertisement = getRspec(input);

		return getModel(rspecAdvertisement);
	}

	// @fixme: expensive/long running method call
	public RSpecContents getRspec(final InputStream input)
			throws JAXBException, XMLStreamException {

		XMLStreamReader xmler = xmlif.createXMLStreamReader(input);

		final JAXBElement<RSpecContents> rspec = unmarshaller.unmarshal(xmler,
				RSpecContents.class);
		final RSpecContents advertisement = rspec.getValue();
		return advertisement;
	}

	@SuppressWarnings("rawtypes")
	public Model getModel(RSpecContents rspec)
			throws MissingRspecElementException {

		final Resource offering = model.createResource(
				AdvertisementConverter.PREFIX + "#advertisement").addProperty(
				RDF.type, Omn_lifecycle.Offering);

		@SuppressWarnings("unchecked")
		final List rspecObjects = (List) rspec.getAnyOrNodeOrLink();

		for (Object rspecObject : rspecObjects) {
			tryExtractNode(rspecObject, offering);
			tryExtractLink(rspecObject, offering);
			tryExtractExternalRef(rspecObject, offering);
			tryExtractSharedVlan(rspecObject, offering);
			tryExtractRoutableAddresses(rspecObject, offering);
			tryExtractOpstate(rspecObject, offering);
			tryExtractStitching(rspecObject, offering);
		}

		return model;
	}

	private void tryExtractStitching(Object rspecObject, Resource offering)
			throws MissingRspecElementException {
		if (rspecObject.toString().contains("stitching")) {

			Model model = offering.getModel();
			Resource stitchResource = model.createResource();
			stitchResource.addProperty(RDF.type, Omn_resource.Stitching);

			ElementNSImpl stitch = ((org.apache.xerces.dom.ElementNSImpl) rspecObject);
			NamedNodeMap attributes = stitch.getAttributes();
			for (int i = 0; i < attributes.getLength(); i++) {
				if (attributes.item(i).getNodeName().equals("lastUpdateTime")) {
					String lastUpdate = attributes.item(i).getNodeValue();
					stitchResource.addProperty(Omn_domain_pc.lastUpdateTime,
							lastUpdate);
				}
			}

			NodeList children = ((org.apache.xerces.dom.ElementNSImpl) rspecObject)
					.getChildNodes();

			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);

				if (child.getNodeName().contains("path")) {
					Resource path = model.createResource();
					path.addProperty(RDF.type, Omn_resource.Path);

					NamedNodeMap pathAttributes = child.getAttributes();
					for (int j = 0; j < pathAttributes.getLength(); j++) {
						if (pathAttributes.item(j).getNodeName().equals("id")) {
							String id = pathAttributes.item(j).getNodeValue();
							path.addProperty(Omn_lifecycle.hasID, id);
						}
					}
					// extractHops(path, child);
					stitchResource.addProperty(Omn.hasResource, path);
				}
			}

			offering.addProperty(Omn.hasResource, stitchResource);
		}
	}

	private void tryExtractInterface(Object rspecObject, Resource omnResource) {

		try {
			@SuppressWarnings("unchecked")
			final JAXBElement<InterfaceContents> nodeJaxb = (JAXBElement<InterfaceContents>) rspecObject;
			final InterfaceContents content = nodeJaxb.getValue();

			Model outputModel = omnResource.getModel();
			Resource interfaceResource = outputModel.createResource();

			List<Object> interfaces = content.getAnyOrIpOrMonitoring();
			for (int i = 0; i < interfaces.size(); i++) {
				Object interfaceObject = interfaces.get(i);
				// tryExtractIPAddress(interfaceObject, interfaceResource);
			}

			interfaceResource.addProperty(RDF.type, Omn_resource.Interface);
			omnResource.addProperty(Omn_resource.hasInterface,
					interfaceResource);

			if (content.getComponentId() != null) {
				interfaceResource.addProperty(Omn_lifecycle.hasComponentID,
						content.getComponentId());
			}

			if (content.getRole() != null) {
				interfaceResource.addProperty(Omn_lifecycle.hasRole,
						content.getRole());
			}

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}

	}

	private void tryExtractRoutableAddresses(Object rspecObject,
			Resource offering) {
		try {

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}

	}

	private void tryExtractSharedVlan(Object rspecObject, Resource offering) {
		try {
			@SuppressWarnings("unchecked")
			final RspecSharedVlan vlan = (RspecSharedVlan) rspecObject;
			Resource sharedVlan = offering.getModel().createResource();
			sharedVlan.addProperty(RDF.type, Omn_domain_pc.SharedVlan);

			List<Available> availables = vlan.getAvailable();
			for (Available available : availables) {

				Resource availableOmn = offering.getModel().createResource();

				if (available.isRestricted() != null) {
					String restricted = available.isRestricted().toString();
					if (restricted != null) {
						availableOmn.addProperty(Omn_domain_pc.restricted,
								restricted);
					}
				}

				if (available.getName() != null) {
					availableOmn.addProperty(RDFS.label, available.getName());
				}
				sharedVlan
						.addProperty(Omn_domain_pc.hasAvailable, availableOmn);
			}

			offering.addProperty(Omn.hasResource, sharedVlan);
		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}

	}

	private void tryExtractExternalRef(Object rspecObject, Resource offering)
			throws MissingRspecElementException {
		try {
			@SuppressWarnings("unchecked")
			final JAXBElement<ExternalReferenceContents> exRefJaxb = (JAXBElement<ExternalReferenceContents>) rspecObject;
			final ExternalReferenceContents exRef = exRefJaxb.getValue();

			// <xs:attribute name="component_id" use="required"/>
			if (exRef.getComponentId() == null) {
				throw new MissingRspecElementException(
						"ExternalReferenceContents > component_id");
			}
			offering.addProperty(Omn_lifecycle.hasID, exRef.getComponentId());

			if (exRef.getComponentManagerId() != null) {
				offering.addProperty(Omn_lifecycle.managedBy,
						exRef.getComponentManagerId());
			}

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractLink(final Object rspecObject,
			final Resource topology) throws MissingRspecElementException {
		try {
			@SuppressWarnings("unchecked")
			final JAXBElement<LinkContents> nodeJaxb = (JAXBElement<LinkContents>) rspecObject;
			final LinkContents link = nodeJaxb.getValue();
			final Resource linkResource = topology.getModel().createResource(
					link.getComponentId());

			// component_id is required
			if (link.getComponentId() == null) {
				throw new MissingRspecElementException(
						"LinkContents > component_id ");
			}
			String componentId = link.getComponentId();
			linkResource.addProperty(Omn_lifecycle.hasComponentID, componentId);

			String componentName = link.getComponentName();
			if (componentName != null) {
				linkResource.addProperty(Omn_lifecycle.hasComponentName,
						componentName);
			}

			for (Object o : link.getAnyOrPropertyOrLinkType()) {
				if (o instanceof JAXBElement) {
					JAXBElement<?> linkElement = (JAXBElement<?>) o;
					if (linkElement.getDeclaredType().equals(
							InterfaceRefContents.class)) {
						extractInterfaceRefs(linkElement, linkResource);
					} else if (linkElement.getDeclaredType().equals(
							LinkPropertyContents.class)) {
						extractLinkProperties(linkElement, linkResource);
					}
				} else if (o.getClass().equals(ComponentManager.class)) {
					extractComponentManager(o, linkResource);
				} else if (o.getClass().equals(LinkType.class)) {
					extractLinkType(o, linkResource);
				} else {
					AdvertisementConverter.LOG.log(Level.INFO,
							"Found unknown link extension: " + o);
				}
			}

			linkResource.addProperty(RDF.type, Omn_resource.Link);
			linkResource.addProperty(Omn.isResourceOf, topology);
			topology.addProperty(Omn.hasResource, linkResource);

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void extractLinkType(Object o, Resource linkResource)
			throws MissingRspecElementException {

		final LinkType content = (LinkType) o;

		// name required
		if (content.getName() == null) {
			throw new MissingRspecElementException("link_type > name");
		}
		linkResource.addProperty(Omn_lifecycle.hasLinkName, content.getName());

	}

	private void extractComponentManager(Object o, Resource linkResource) {

		final ComponentManager content = (ComponentManager) o;

		if (content.getName() != null) {
			linkResource.addProperty(Omn_lifecycle.hasComponentManagerName,
					content.getName());

		}

	}

	private void extractLinkProperties(JAXBElement<?> linkElement,
			Resource linkResource) throws MissingRspecElementException {

		final LinkPropertyContents content = (LinkPropertyContents) linkElement
				.getValue();

		String sourceID = content.getSourceId();
		String destID = content.getDestId();

		if (sourceID == null || destID == null) {
			throw new MissingRspecElementException(
					"LinkPropertyContents > source_id/dest_id");
		}

		Resource linkPropertyResource = linkResource.getModel()
				.createResource();
		linkPropertyResource.addProperty(RDF.type, Omn_resource.LinkProperty);
		linkPropertyResource.addProperty(Omn_resource.hasSink, destID);
		linkPropertyResource.addProperty(Omn_resource.hasSource, sourceID);

		String capacity = content.getCapacity();
		if (capacity != null) {
			linkPropertyResource.addProperty(Omn_domain_pc.hasCapacity,
					capacity);
		}

		String latency = content.getLatency();
		if (latency != null) {
			linkPropertyResource.addProperty(Omn_domain_pc.hasLatency, latency);
		}

		String packetLoss = content.getPacketLoss();
		if (packetLoss != null) {
			linkPropertyResource.addProperty(Omn_domain_pc.hasPacketLoss,
					packetLoss);
		}

		linkResource
				.addProperty(Omn_resource.hasProperty, linkPropertyResource);

	}

	private void extractInterfaceRefs(JAXBElement<?> linkElement,
			Resource linkResource) {
		final InterfaceRefContents content = (InterfaceRefContents) linkElement
				.getValue();

		Resource interfaceResource = linkResource.getModel().createResource();
		interfaceResource.addProperty(Omn_lifecycle.hasComponentID,
				content.getComponentId());

		linkResource.addProperty(Omn_resource.hasInterface, interfaceResource);

	}

	private void tryExtractOpstate(Object rspecObject, Resource offering)
			throws MissingRspecElementException {
		try {
			@SuppressWarnings("unchecked")
			final RspecOpstate nodeJaxb = (RspecOpstate) rspecObject;

			Resource opstate = offering.getModel().createResource(
					UUID.randomUUID().toString());
			opstate.addProperty(RDF.type, Omn_lifecycle.Opstate);

			// extract start
			// start is required
			String start = nodeJaxb.getStart();
			if (start == null) {
				throw new MissingRspecElementException("RspecOpstate > start");
			}
			opstate.addProperty(Omn_lifecycle.hasStartState,
					CommonMethods.convertGeniStateToOmn(start));

			// extract aggregate manager id
			// aggregate_manager_id is required
			String aggregateManagerId = nodeJaxb.getAggregateManagerId();
			if (aggregateManagerId == null) {
				throw new MissingRspecElementException(
						"RspecOpstate > aggregate_manager_id");
			}
			opstate.addProperty(Omn_lifecycle.managedBy, aggregateManagerId);

			List<Object> sliverStates = nodeJaxb.getSliverTypeOrState();
			for (Object object : sliverStates) {
				tryExtractOpstateSliverType(object, opstate);
				tryExtractOpstateState(object, opstate);
			}

			offering.addProperty(Omn.hasResource, opstate);
		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractOpstateState(Object object, Resource opstate) {
		try {
			@SuppressWarnings("unchecked")
			StateSpec stateSpecs = (StateSpec) object;

			Resource state = opstate.getModel().createResource();

			String geniType = stateSpecs.getName();
			OntClass omnType = CommonMethods.convertGeniStateToOmn(geniType);
			if (omnType != null) {
				state.addProperty(RDF.type, omnType);
				opstate.addProperty(Omn_lifecycle.hasState, state);
			} else {
				return;
			}

			List<Object> actionWaitDescription = stateSpecs
					.getActionOrWaitOrDescription();
			for (Object awd : actionWaitDescription) {
				tryExtractAction(awd, state);
				tryExtractWait(awd, state);
				tryExtractDescription(awd, state);
			}

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractAction(Object awd, Resource state) {
		try {
			@SuppressWarnings("unchecked")
			ActionSpec action = (ActionSpec) awd;

			String description = action.getDescription();
			String next = action.getNext();
			String name = action.getName();
			Resource actionResource = state.getModel().createResource();
			actionResource.addProperty(RDF.type, Omn_lifecycle.Action);

			if (next != null) {
				OntClass omnNext = CommonMethods.convertGeniStateToOmn(next);
				actionResource.addProperty(Omn_lifecycle.hasNext, omnNext);
			}
			if (description != null) {
				actionResource.addProperty(RDFS.comment, description);
			}

			if (name != null) {
				OntClass omnType = CommonMethods.convertGeniStateToOmn(name);
				actionResource.addProperty(Omn_lifecycle.hasStateName, omnType);
			}

			state.addProperty(Omn_lifecycle.hasAction, actionResource);

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractWait(Object awd, Resource state) {
		try {
			@SuppressWarnings("unchecked")
			WaitSpec wait = (WaitSpec) awd;

			String description = wait.getDescription();
			String next = wait.getNext();
			String type = wait.getType();
			Resource waitResource = state.getModel().createResource();
			waitResource.addProperty(RDF.type, Omn_lifecycle.Wait);

			if (next != null) {
				OntClass omnNext = CommonMethods.convertGeniStateToOmn(next);
				waitResource.addProperty(Omn_lifecycle.hasNext, omnNext);
			}
			if (description != null) {
				waitResource.addProperty(RDFS.comment, description);
			}

			if (type != null) {
				OntClass omnType = CommonMethods.convertGeniStateToOmn(type);
				waitResource.addProperty(Omn_lifecycle.hasType, omnType);
			}

			state.addProperty(Omn_lifecycle.hasWait, waitResource);

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractDescription(Object awd, Resource state) {
		try {
			@SuppressWarnings("unchecked")
			String description = (String) awd;
			state.addProperty(RDFS.comment, description);

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractOpstateSliverType(Object object, Resource opstate)
			throws MissingRspecElementException {

		try {
			@SuppressWarnings("unchecked")
			info.openmultinet.ontology.translators.geni.jaxb.advertisement.SliverType sliver = (info.openmultinet.ontology.translators.geni.jaxb.advertisement.SliverType) object;

			final Resource omnSliver = opstate.getModel().createResource();

			// name is required
			String name = sliver.getName();
			if (name == null) {
				throw new MissingRspecElementException(
						"opstate > slivertype > name");
			}
			omnSliver.addProperty(RDFS.label, name);
			opstate.addProperty(Omn_lifecycle.canBeImplementedBy, omnSliver);

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractNode(final Object object, final Resource topology)
			throws MissingRspecElementException {
		try {
			@SuppressWarnings("unchecked")
			final JAXBElement<NodeContents> nodeJaxb = (JAXBElement<NodeContents>) object;
			final NodeContents rspecNode = nodeJaxb.getValue();

			// String componentId =
			// AbstractConverter.generateUrlFromUrn(rspecNode
			// .getComponentId());
			String componentId = rspecNode.getComponentId();
			final Resource omnNode = topology.getModel().createResource(
					componentId);

			omnNode.addProperty(RDF.type, Omn_resource.Node);
			omnNode.addProperty(Omn.isResourceOf, topology);

			if (rspecNode.getComponentManagerId() != null) {
				RDFNode parent = ResourceFactory.createResource(rspecNode
						.getComponentManagerId());
				omnNode.addProperty(Omn_lifecycle.parentOf, parent);
			}

			topology.getModel().addLiteral(omnNode, Omn_resource.isExclusive,
					rspecNode.isExclusive());

			if (rspecNode.getComponentName() != null) {
				omnNode.addLiteral(RDFS.label, rspecNode.getComponentName());
				omnNode.addProperty(Omn_lifecycle.hasComponentName,
						rspecNode.getComponentName());
			}

			for (Object rspecNodeObject : rspecNode
					.getAnyOrRelationOrLocation()) {
				tryExtractCloud(rspecNodeObject, omnNode);
				tryExtractHardwareType(rspecNodeObject, omnNode);
				tryExtractSliverType(rspecNodeObject, omnNode);
				tryExtractLocation(rspecNodeObject, omnNode);
				tryExtractAvailability(rspecNodeObject, omnNode);
				tryExtractMonitoring(rspecNodeObject, omnNode);
				tryExtractInterface(rspecNodeObject, omnNode);
				tryExtractEmulabFd(rspecNodeObject, omnNode);

			}

			topology.addProperty(Omn.hasResource, omnNode);
		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractCloud(Object rspecNodeObject, Resource omnNode) {
		try {
			@SuppressWarnings("unchecked")
			final Cloud cloudJaxb = (Cloud) rspecNodeObject;
			final Resource cloudResource = omnNode.getModel().createResource();

			omnNode.addProperty(RDF.type, Omn_resource.Cloud);
			omnNode.addProperty(Omn.hasResource, cloudResource);
			cloudResource.addProperty(Omn.isResourceOf, omnNode);

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractEmulabFd(Object rspecNodeObject, Resource omnNode) {
		try {
			@SuppressWarnings("unchecked")
			Fd featureDescription = (Fd) rspecNodeObject;

			Resource fdResource = model.createResource();
			fdResource.addProperty(RDF.type, Omn_domain_pc.FeatureDescription);

			// name is required
			String name = featureDescription.getName();
			fdResource.addProperty(Omn_domain_pc.hasEmulabFdName, name);

			// weight is required
			String weight = featureDescription.getWeight();
			fdResource.addProperty(Omn_domain_pc.hasEmulabFdWeight, weight);

			// violatable is not required
			String violatable = featureDescription.getViolatable();
			if (violatable != null) {
				fdResource.addProperty(Omn_domain_pc.emulabFdViolatable,
						violatable);
			}

			// local operator is not required
			String localOperator = featureDescription.getLocalOperator();
			if (localOperator != null) {
				fdResource.addProperty(Omn_domain_pc.hasEmulabFdLocalOperator,
						localOperator);
			}

			// global operator is not required
			String globalOperator = featureDescription.getGlobalOperator();
			if (globalOperator != null) {
				fdResource.addProperty(Omn_domain_pc.hasEmulabFdGlobalOperator,
						globalOperator);
			}

			omnNode.addProperty(Omn.hasResource, fdResource);

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}

	}

	private void tryExtractEmulabNodeType(Object rspecHwObject, Resource omnHw) {
		try {
			@SuppressWarnings("unchecked")
			NodeType nodeType = (NodeType) rspecHwObject;

			String nodeTypeSlots = nodeType.getTypeSlots();
			omnHw.addProperty(Omn_domain_pc.hasEmulabNodeTypeSlots,
					nodeTypeSlots);

			String staticSlot = nodeType.getStatic();
			if (staticSlot != null) {
				omnHw.addProperty(Omn_domain_pc.emulabNodeTypeStatic,
						staticSlot);
			}

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractMonitoring(Object rspecNodeObject, Resource omnNode) {
		try {
			@SuppressWarnings("unchecked")
			Monitoring monitor = (Monitoring) rspecNodeObject;
			Resource monitoringResource = model.createResource(UUID
					.randomUUID().toString());
			if (monitor.getUri() != null && monitor.getUri() != "") {
				monitoringResource.addProperty(Omn_service.hasURI,
						monitor.getUri());
			}
			if (monitor.getType() != null && monitor.getType() != "") {
				monitoringResource.addProperty(RDF.type, monitor.getType());
				monitoringResource.addProperty(RDFS.label,
						AbstractConverter.getName(monitor.getType()));
			}
			omnNode.addProperty(Omn_lifecycle.usesService, monitoringResource);
		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractAvailability(Object rspecNodeObject, Resource omnNode) {
		try {
			@SuppressWarnings("unchecked")
			final JAXBElement<AvailableContents> availablityJaxb = (JAXBElement<AvailableContents>) rspecNodeObject;
			final AvailableContents availability = availablityJaxb.getValue();

			omnNode.addLiteral(Omn_resource.isAvailable, availability.isNow());
		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractLocation(Object rspecNodeObject, Resource omnNode)
			throws MissingRspecElementException {
		try {
			@SuppressWarnings("unchecked")
			final JAXBElement<LocationContents> locationJaxb = (JAXBElement<LocationContents>) rspecNodeObject;
			final LocationContents location = locationJaxb.getValue();

			if (location != null) {
				String latitude = location.getLatitude();
				String longitude = location.getLongitude();
				String country = location.getCountry();

				// country is required, when location is specified
				if (country == null) {
					throw new MissingRspecElementException(
							"LocationContents > country");
				} else {
					omnNode.addProperty(Omn_resource.country, country);
				}

				if (latitude != null) {
					omnNode.addProperty(Geo.lat, latitude);
				}
				if (longitude != null) {
					omnNode.addProperty(Geo.long_, longitude);
				}
			}
		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractHardwareType(Object rspecNodeObject, Resource omnNode) {
		try {
			@SuppressWarnings("unchecked")
			final JAXBElement<HardwareTypeContents> hwJaxb = (JAXBElement<HardwareTypeContents>) rspecNodeObject;
			final HardwareTypeContents hw = hwJaxb.getValue();

			final Resource omnHw = omnNode.getModel().createResource();
			RDFNode type = ResourceFactory.createProperty(hw.getName());

			// TODO: get rid of this line
			// omnNode.addProperty(RDF.type, type);

			omnHw.addProperty(RDFS.label, type.toString());
			omnHw.addProperty(RDF.type, Omn_domain_pc.HardwareType);
			for (Object hwObject : hw.getAny()) {
				tryExtractEmulabNodeType(hwObject, omnHw);
			}
			omnNode.addProperty(Omn_domain_pc.hasHardwareType, omnHw);

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		} catch (final InvalidPropertyURIException e) {
			AdvertisementConverter.LOG.info(e.getMessage());
		}
	}

	private void tryExtractSliverType(Object rspecNodeObject,
			Resource omnResource) throws MissingRspecElementException {
		// try {
		// @SuppressWarnings("unchecked")
		// final JAXBElement<SliverType> sliverJaxb = (JAXBElement<SliverType>)
		// rspecNodeObject;
		// final SliverType sliver = sliverJaxb.getValue();
		//
		// final Resource omnSliver = omnNode.getModel().createResource(
		// sliver.getName());
		//
		//
		// RDFNode type = ResourceFactory.createProperty(sliver.getName());
		//
		// omnNode.addProperty(Omn_lifecycle.canImplement, type);
		//
		// for (Object rspecSliverObject : sliver.getAnyOrDiskImage()) {
		// tryExtractCpus(rspecSliverObject, omnSliver);
		// tryExtractDiskImage(rspecSliverObject, omnSliver);
		// }
		//
		// // RDFNode type = ResourceFactory.createProperty(sliver.getName());
		// omnNode.addProperty(Omn_lifecycle.canImplement, type);
		// } catch (final ClassCastException e) {
		// AdvertisementConverter.LOG.finer(e.getMessage());
		// }
		try {
			@SuppressWarnings("unchecked")
			final JAXBElement<SliverType> sliverJaxb = (JAXBElement<SliverType>) rspecNodeObject;
			final SliverType sliverType = sliverJaxb.getValue();
			String sliverName = sliverType.getName();
			if (sliverName == null) {
				throw new MissingRspecElementException(
						"SliverTypeContents > name");
			}
			Resource sliverTypeResource = null;
			// Note: Do not change sliver type here, as Fiteagle will
			// not work
			if (AbstractConverter.isUrl(sliverName)) {
				sliverTypeResource = omnResource.getModel().createResource(
						sliverName);
			} else {
				sliverTypeResource = omnResource.getModel().createResource();
			}
			omnResource.addProperty(Omn_resource.hasSliverType,
					sliverTypeResource);
			sliverTypeResource.addProperty(Omn_lifecycle.hasSliverName,
					sliverName);
			sliverTypeResource.addProperty(RDF.type, Omn_resource.SliverType);

			for (Object rspecSliverObject : sliverType.getAnyOrDiskImage()) {
				tryExtractCpus(rspecSliverObject, sliverTypeResource);
				tryExtractDiskImage(rspecSliverObject, sliverTypeResource);
			}
		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void tryExtractCpus(Object rspecSliverObject, Resource omnSliver) {
		if (rspecSliverObject
				.getClass()
				.equals(info.openmultinet.ontology.translators.geni.jaxb.advertisement.Pc.class)) {
			Pc pc = (Pc) rspecSliverObject;

			if (pc.getCpus() != null) {
				omnSliver.addLiteral(Omn_domain_pc.hasCPU, pc.getCpus());
			}
		}
	}

	private void tryExtractDiskImage(Object rspecSliverObject,
			Resource omnSliver) {
		try {
			@SuppressWarnings("unchecked")
			JAXBElement<DiskImageContents> diJaxb = (JAXBElement<DiskImageContents>) rspecSliverObject;
			DiskImageContents diskImageContents = diJaxb.getValue();

			String diskImageURL = diskImageContents.getUrl();
			Resource diskImage = model.createResource(diskImageURL);
			diskImage.addProperty(RDF.type, Omn_domain_pc.DiskImage);

			// add name info
			String name = diskImageContents.getName();
			diskImage.addLiteral(Omn_domain_pc.hasDiskimageLabel, name);
			// omnSliver.addProperty(Omn_lifecycle.canImplement, diskImage);
			omnSliver.addProperty(Omn_domain_pc.hasDiskImage, diskImage);

			String os = diskImageContents.getOs();
			if (os != null) {
				diskImage.addLiteral(Omn_domain_pc.hasDiskimageOS, os);
			}

			String version = diskImageContents.getVersion();
			if (version != null) {
				diskImage
						.addLiteral(Omn_domain_pc.hasDiskimageVersion, version);
			}

			String description = diskImageContents.getDescription();
			if (description != null) {
				diskImage.addLiteral(Omn_domain_pc.hasDiskimageDescription,
						description);
			}

			String url = diskImageContents.getUrl();
			if (url != null) {
				diskImage.addLiteral(Omn_domain_pc.hasDiskimageURI, url);
			}

			System.out.println(rspecSliverObject.toString());
			@SuppressWarnings("unchecked")
			JAXBElement<DiskImage> diskImageElement = (JAXBElement<DiskImage>) rspecSliverObject;
			String defaultString = diskImageElement.getValue().getDefault();

			if (defaultString != null) {
				diskImage.addLiteral(Omn_domain_pc.diskimageDefault,
						defaultString);
			}

		} catch (final ClassCastException e) {
			AdvertisementConverter.LOG.info(e.getMessage());
		} catch (final InvalidPropertyURIException e) {
			AdvertisementConverter.LOG.info(e.getMessage());
		}

	}

	public String getRSpec(final Model model) throws JAXBException,
			InvalidModelException {
		final JAXBElement<RSpecContents> rspec = getRSpecObject(model);

		return AbstractConverter.toString(rspec, AdvertisementConverter.JAXB);
	}

	public JAXBElement<RSpecContents> getRSpecObject(final Model model)
			throws InvalidModelException {
		final RSpecContents advertisement = new RSpecContents();
		advertisement.setType(RspecTypeContents.ADVERTISEMENT);
		advertisement.setGeneratedBy(AbstractConverter.VENDOR);
		setTimeInformation(advertisement);

		model2rspec(model, advertisement);
		final JAXBElement<RSpecContents> rspec = this.of
				.createRspec(advertisement);
		return rspec;
	}

	private void model2rspec(final Model model, final RSpecContents ad)
			throws InvalidModelException {
		final List<Resource> groups = model.listSubjectsWithProperty(RDF.type,
				Omn_lifecycle.Offering).toList();
		AbstractConverter.validateModel(groups);
		final Resource group = groups.iterator().next();

		// set external_ref
		if (group.hasProperty(Omn_lifecycle.hasID)) {
			ExternalReferenceContents exrefContents = of
					.createExternalReferenceContents();
			String componentId = group.getProperty(Omn_lifecycle.hasID)
					.getObject().asLiteral().getString();
			exrefContents.setComponentId(componentId);

			if (group.hasProperty(Omn_lifecycle.managedBy)) {
				String component_manager_id = group
						.getProperty(Omn_lifecycle.managedBy).getObject()
						.asLiteral().getString();
				exrefContents.setComponentManagerId(component_manager_id);
			}

			ad.getAnyOrNodeOrLink().add(of.createExternalRef(exrefContents));
		}

		final List<Statement> resources = group.listProperties(Omn.hasResource)
				.toList();

		convertStatementsToNodesAndLinks(ad, resources);
	}

	private void convertStatementsToNodesAndLinks(
			final RSpecContents advertisement,
			final List<Statement> omnResources) {
		for (final Statement omnResource : omnResources) {
			if (!omnResource.getResource().hasProperty(RDF.type,
					Omn_resource.Link)
					&& !omnResource.getResource().hasProperty(RDF.type,
							Omn_lifecycle.Opstate)
					&& !omnResource.getResource().hasProperty(RDF.type,
							Omn_domain_pc.SharedVlan)
					&& !omnResource.getResource().hasProperty(RDF.type,
							Omn_resource.Stitching)) {
				// @todo: check type of resource here and not only generate
				// nodes
				final NodeContents geniNode = new NodeContents();

				setCloud(omnResource, geniNode);
				setComponentDetails(omnResource, geniNode);
				setComponentManagerId(omnResource, geniNode);
				setHardwareTypes(omnResource, geniNode);
				setSliverTypes(omnResource, geniNode);
				setLocation(omnResource, geniNode);
				setAvailability(omnResource, geniNode);
				setMonitoringService(omnResource, geniNode);
				setInterface(omnResource, geniNode);
				setFd(omnResource, geniNode);

				ResIterator infrastructures = omnResource.getModel()
						.listResourcesWithProperty(Omn.isResourceOf,
								Omn_federation.Infrastructure);
				if (infrastructures.hasNext()) {
					Resource infrastructure = infrastructures.next();
					geniNode.setComponentManagerId(infrastructure.getURI());
				}

				advertisement.getAnyOrNodeOrLink().add(
						this.of.createNode(geniNode));

			} else if (omnResource.getResource().hasProperty(RDF.type,
					Omn_resource.Link)) {
				final LinkContents link = new LinkContents();

				setLinkDetails(omnResource, link);
				setInterfaceRefs(omnResource, link);
				setLinkProperties(omnResource, link);

				advertisement.getAnyOrNodeOrLink().add(
						new ObjectFactory().createLink(link));

			} else if (omnResource.getResource().hasProperty(RDF.type,
					Omn_domain_pc.SharedVlan)) {

				RspecSharedVlan sharedVlan = this.of.createRspecSharedVlan();

				StmtIterator availables = omnResource.getResource()
						.listProperties(Omn_domain_pc.hasAvailable);

				while (availables.hasNext()) {
					Resource availableResource = availables.next().getObject()
							.asResource();
					Available available = this.of.createAvailable();

					if (availableResource.hasProperty(Omn_domain_pc.restricted)) {
						boolean restricted = availableResource
								.getProperty(Omn_domain_pc.restricted)
								.getObject().asLiteral().getBoolean();
						available.setRestricted(restricted);
					}
					if (availableResource.hasProperty(RDFS.label)) {
						String name = availableResource.getProperty(RDFS.label)
								.getObject().asLiteral().getString();
						available.setName(name);
					}
					sharedVlan.getAvailable().add(available);
				}

				advertisement.getAnyOrNodeOrLink().add(sharedVlan);

			} else if (omnResource.getResource().hasProperty(RDF.type,
					Omn_lifecycle.Opstate)) {

				RspecOpstate rspecOpstate = this.of.createRspecOpstate();

				setOpstateAttributes(omnResource, rspecOpstate);
				setSliverTypes(omnResource, rspecOpstate);
				setStates(omnResource, rspecOpstate);

				advertisement.getAnyOrNodeOrLink().add(rspecOpstate);

			} else if (omnResource.getResource().hasProperty(RDF.type,
					Omn_resource.Stitching)) {
				ObjectFactory of = new ObjectFactory();

				StitchContent stitchContent = of.createStitchContent();
				setStitching(omnResource, stitchContent);

				JAXBElement<StitchContent> stitching = of
						.createStitching(stitchContent);

				advertisement.getAnyOrNodeOrLink().add(stitching);
			}
		}
	}

	private void setStitching(Statement resource, StitchContent stitchContent) {
		if (resource.getResource().hasProperty(Omn_domain_pc.lastUpdateTime)) {
			String lastUpdateTime = resource.getResource()
					.getProperty(Omn_domain_pc.lastUpdateTime).getObject()
					.asLiteral().getString();
			stitchContent.setLastUpdateTime(lastUpdateTime);
		}

		StmtIterator resources = resource.getResource().listProperties(
				Omn.hasResource);

		while (resources.hasNext()) {
			Resource object = resources.next().getObject().asResource();
			if (object.hasProperty(RDF.type, Omn_resource.Path)) {
				final PathContent pathContent = new PathContent();

				if (object.hasProperty(Omn_lifecycle.hasID)) {
					String id = object.getProperty(Omn_lifecycle.hasID)
							.getObject().asLiteral().getString();
					pathContent.setId(id);
				}

				StmtIterator hops = object.listProperties(Omn.hasResource);

				while (hops.hasNext()) {
					HopContent hopContent = new HopContent();
					Resource hopObject = hops.next().getObject().asResource();
					if (hopObject.hasProperty(Omn_lifecycle.hasID)) {
						String id = hopObject.getProperty(Omn_lifecycle.hasID)
								.getObject().asLiteral().getString();
						hopContent.setId(id);
					}
					if (hopObject.hasProperty(Omn_domain_pc.hasHopType)) {
						String type = hopObject
								.getProperty(Omn_domain_pc.hasHopType)
								.getObject().asLiteral().getString();
						hopContent.setType(type);
					}
					if (hopObject.hasProperty(Omn_domain_pc.hasNextHop)) {
						String nextHop = hopObject
								.getProperty(Omn_domain_pc.hasNextHop)
								.getObject().asLiteral().getString();

						NextHopContent nextHopContent = new NextHopContent();
						nextHopContent.setValue(nextHop);
						hopContent.getNextHop().add(nextHopContent);
					}
					pathContent.getHop().add(hopContent);
				}

				stitchContent.getPath().add(pathContent);
			}
		}

	}

	private void setCloud(Statement omnResource, NodeContents geniNode) {
		if (omnResource.getResource().hasProperty(RDF.type, Omn_resource.Cloud)) {
			Cloud cloud = new ObjectFactory().createCloud();
			geniNode.getAnyOrRelationOrLocation().add(cloud);
		}

	}

	private void setLinkProperties(Statement resource, LinkContents link) {
		List<Statement> linkProperties = resource.getResource()
				.listProperties(Omn_resource.hasProperty).toList();

		for (Statement linkPropertyStatement : linkProperties) {

			LinkPropertyContents newLinkProperty = new ObjectFactory()
					.createLinkPropertyContents();

			Resource linkResource = linkPropertyStatement.getResource();

			String sinkId = linkResource.getProperty(Omn_resource.hasSink)
					.getObject().asLiteral().getString();
			newLinkProperty.setDestId(sinkId);

			String sourceId = linkResource.getProperty(Omn_resource.hasSource)
					.getObject().asLiteral().getString();
			newLinkProperty.setSourceId(sourceId);

			if (linkResource.hasProperty(Omn_domain_pc.hasLatency)) {
				String latency = linkResource
						.getProperty(Omn_domain_pc.hasLatency).getObject()
						.asLiteral().getString();
				newLinkProperty.setLatency(latency);
			}

			if (linkResource.hasProperty(Omn_domain_pc.hasPacketLoss)) {
				String packetLoss = linkResource
						.getProperty(Omn_domain_pc.hasPacketLoss).getObject()
						.asLiteral().getString();
				newLinkProperty.setPacketLoss(packetLoss);
			}

			if (linkResource.hasProperty(Omn_domain_pc.hasCapacity)) {
				String capacity = linkResource
						.getProperty(Omn_domain_pc.hasCapacity).getObject()
						.asLiteral().getString();
				newLinkProperty.setCapacity(capacity);
			}

			link.getAnyOrPropertyOrLinkType().add(
					new ObjectFactory().createProperty(newLinkProperty));
		}

	}

	private void setInterfaceRefs(Statement resource, LinkContents link) {
		List<Statement> interfaces = resource.getResource()
				.listProperties(Omn_resource.hasInterface).toList();

		for (Statement interface1 : interfaces) {

			InterfaceRefContents newInterface = new ObjectFactory()
					.createInterfaceRefContents();

			String componentId = interface1.getResource()
					.getProperty(Omn_lifecycle.hasComponentID).getObject()
					.asLiteral().getString();

			newInterface.setComponentId(componentId);

			JAXBElement<InterfaceRefContents> interfaceJaxb = new ObjectFactory()
					.createInterfaceRef(newInterface);

			link.getAnyOrPropertyOrLinkType().add(interfaceJaxb);
		}

	}

	private void setLinkDetails(Statement resource, LinkContents link) {

		List<Statement> linkTypes = resource.getResource()
				.listProperties(Omn_lifecycle.hasLinkName).toList();

		for (Statement linkStatement : linkTypes) {
			String linkName = linkStatement.getObject().asLiteral().getString();
			LinkType linkType = new ObjectFactory().createLinkType();
			linkType.setName(linkName);
			link.getAnyOrPropertyOrLinkType().add(linkType);
		}

		if (resource.getResource().hasProperty(Omn_lifecycle.hasComponentID)) {
			String componentId = resource.getResource()
					.getProperty(Omn_lifecycle.hasComponentID).getObject()
					.asLiteral().getString();
			link.setComponentId(componentId);
		}

		if (resource.getResource().hasProperty(Omn_lifecycle.hasComponentName)) {
			String componentName = resource.getResource()
					.getProperty(Omn_lifecycle.hasComponentName).getObject()
					.asLiteral().getString();
			link.setComponentName(componentName);
		}

		if (resource.getResource().hasProperty(
				Omn_lifecycle.hasComponentManagerName)) {

			List<Statement> componentManagers = resource.getResource()
					.listProperties(Omn_lifecycle.hasComponentManagerName)
					.toList();

			for (final Statement manager : componentManagers) {
				String managerName = manager.getObject().asLiteral()
						.getString();
				ComponentManager rspecManager = new ObjectFactory()
						.createComponentManager();
				rspecManager.setName(managerName);
				link.getAnyOrPropertyOrLinkType().add(rspecManager);
			}

		}

	}

	private void setFd(Statement omnResource, NodeContents geniNode) {
		// TODO Auto-generated method stub

		List<Statement> resources = omnResource.getResource()
				.listProperties(Omn.hasResource).toList();

		for (final Statement resourceStatement : resources) {
			// add emulab node slots
			if (resourceStatement.getResource().hasProperty(RDF.type,
					Omn_domain_pc.FeatureDescription)) {

				// name is required
				Fd fd = of.createFd();
				String name = resourceStatement.getResource()
						.getProperty(Omn_domain_pc.hasEmulabFdName).getObject()
						.asLiteral().getString();
				fd.setName(name);

				// weight is required
				String weight = resourceStatement.getResource()
						.getProperty(Omn_domain_pc.hasEmulabFdWeight)
						.getObject().asLiteral().getString();
				fd.setWeight(weight);

				if (resourceStatement.getResource().hasProperty(
						Omn_domain_pc.emulabFdViolatable)) {
					String violatable = resourceStatement.getResource()
							.getProperty(Omn_domain_pc.emulabFdViolatable)
							.getObject().asLiteral().getString();
					fd.setViolatable(violatable);
				}

				if (resourceStatement.getResource().hasProperty(
						Omn_domain_pc.hasEmulabFdLocalOperator)) {
					String localOperator = resourceStatement
							.getResource()
							.getProperty(Omn_domain_pc.hasEmulabFdLocalOperator)
							.getObject().asLiteral().getString();
					fd.setLocalOperator(localOperator);
				}

				if (resourceStatement.getResource().hasProperty(
						Omn_domain_pc.hasEmulabFdGlobalOperator)) {
					String localOperator = resourceStatement
							.getResource()
							.getProperty(
									Omn_domain_pc.hasEmulabFdGlobalOperator)
							.getObject().asLiteral().getString();
					fd.setGlobalOperator(localOperator);
				}

				geniNode.getAnyOrRelationOrLocation().add(fd);
			}
		}
	}

	private void setInterface(Statement resource, NodeContents nodeContents) {
		// TODO Auto-generated method stub
		List<Statement> interfaces = resource.getResource()
				.listProperties(Omn_resource.hasInterface).toList();

		for (final Statement interface1 : interfaces) {
			InterfaceContents interfaceContents = new ObjectFactory()
					.createInterfaceContents();
			Resource interfaceResource = interface1.getResource();

			if (interfaceResource.hasProperty(Omn_lifecycle.hasComponentID)) {
				interfaceContents.setComponentId(interfaceResource
						.getProperty(Omn_lifecycle.hasComponentID).getObject()
						.asLiteral().toString());
			}

			if (interfaceResource.hasProperty(Omn_lifecycle.hasRole)) {
				interfaceContents.setRole(interfaceResource
						.getProperty(Omn_lifecycle.hasRole).getObject()
						.asLiteral().toString());
			}

			// setIpAddress(interfaceResource, interfaceContents);

			JAXBElement<InterfaceContents> interfaceRspec = new ObjectFactory()
					.createInterface(interfaceContents);
			nodeContents.getAnyOrRelationOrLocation().add(interfaceRspec);
		}
	}

	private void setOpstateAttributes(Statement omnResource,
			RspecOpstate rspecOpstate) {
		// set aggregateManagerId
		String aggregateManagerId = omnResource
				.getProperty(Omn_lifecycle.managedBy).getObject().asLiteral()
				.getString();
		// required
		if (aggregateManagerId == null) {
			aggregateManagerId = "";
		}
		rspecOpstate.setAggregateManagerId(aggregateManagerId);

		// set start state
		Resource start = omnResource.getProperty(Omn_lifecycle.hasStartState)
				.getObject().asResource();
		String geniStart = CommonMethods.convertOmnToGeniState(start);
		rspecOpstate.setStart(geniStart);

	}

	private void setStates(Statement omnResource, RspecOpstate rspecOpstate) {
		// get states
		StmtIterator states = omnResource.getResource().listProperties(
				Omn_lifecycle.hasState);
		while (states.hasNext()) {
			Statement stateStatement = states.next();
			StateSpec stateSpec = of.createStateSpec();

			// set name
			Resource stateResource = stateStatement.getObject().asResource();
			setStateName(stateResource, stateSpec);
			setDescription(stateResource, stateSpec);
			setWait(stateResource, stateSpec);
			setAction(stateResource, stateSpec);
			rspecOpstate.getSliverTypeOrState().add(stateSpec);
		}
	}

	private void setAction(Resource stateResource, StateSpec stateSpec) {

		StmtIterator actions = stateResource
				.listProperties(Omn_lifecycle.hasAction);
		while (actions.hasNext()) {
			Statement typeStatement = actions.next();
			Resource action = typeStatement.getObject().asResource();
			ActionSpec actionSpec = of.createActionSpec();

			// set next
			Resource next = action.getProperty(Omn_lifecycle.hasNext)
					.getObject().asResource();
			if (CommonMethods.isOmnState(next)) {
				String geniState = CommonMethods.convertOmnToGeniState(next);
				actionSpec.setNext(geniState);
			}

			// set name
			Resource name = action.getProperty(Omn_lifecycle.hasStateName)
					.getObject().asResource();
			if (CommonMethods.isOmnState(name)) {
				String geniState = CommonMethods.convertOmnToGeniState(name);
				actionSpec.setName(geniState);
			}

			// set description
			if (action.hasProperty(RDFS.comment)) {
				String description = action.getProperty(RDFS.comment)
						.getObject().asLiteral().getString();
				actionSpec.setDescription(description);
			}

			stateSpec.getActionOrWaitOrDescription().add(actionSpec);
		}
	}

	private void setWait(Resource stateResource, StateSpec stateSpec) {

		StmtIterator waits = stateResource
				.listProperties(Omn_lifecycle.hasWait);
		while (waits.hasNext()) {
			Statement typeStatement = waits.next();
			Resource wait = typeStatement.getObject().asResource();
			WaitSpec waitSpec = of.createWaitSpec();

			// set next
			Resource next = wait.getProperty(Omn_lifecycle.hasNext).getObject()
					.asResource();
			if (CommonMethods.isOmnState(next)) {
				String geniState = CommonMethods.convertOmnToGeniState(next);
				waitSpec.setNext(geniState);
			}

			// set type
			Resource type = wait.getProperty(Omn_lifecycle.hasType).getObject()
					.asResource();
			if (CommonMethods.isOmnState(type)) {
				String geniState = CommonMethods.convertOmnToGeniState(type);
				waitSpec.setType(geniState);
			}

			stateSpec.getActionOrWaitOrDescription().add(waitSpec);
		}
	}

	private void setStateName(Resource stateResource, StateSpec stateSpec) {

		StmtIterator types = stateResource.listProperties(RDF.type);
		while (types.hasNext()) {
			Statement typeStatement = types.next();
			Resource type = typeStatement.getObject().asResource();

			if (CommonMethods.isOmnState(type)) {
				String geniState = CommonMethods.convertOmnToGeniState(type);
				stateSpec.setName(geniState);
			}
		}
	}

	private void setDescription(Resource stateResource, StateSpec stateSpec) {
		StmtIterator descriptions = stateResource.listProperties(RDFS.comment);
		while (descriptions.hasNext()) {
			Statement descriptionStatement = descriptions.next();
			String descriptionString = descriptionStatement.getObject()
					.asLiteral().getString();
			stateSpec.getActionOrWaitOrDescription().add(descriptionString);
		}
	}

	private void setSliverTypes(Statement omnResource, RspecOpstate rspecOpstate) {
		// get sliver types
		StmtIterator canBeImplementBy = omnResource.getResource()
				.listProperties(Omn_lifecycle.canBeImplementedBy);
		info.openmultinet.ontology.translators.geni.jaxb.advertisement.SliverType sliver;

		List<Object> sliverTypeOrState = rspecOpstate.getSliverTypeOrState();
		while (canBeImplementBy.hasNext()) {
			Statement omnSliver = canBeImplementBy.next();

			sliver = of.createSliverType();

			String name = "";
			if (omnSliver.getObject().asResource().hasProperty(RDFS.label)) {
				name = omnSliver.getObject().asResource()
						.getProperty(RDFS.label).getObject().asLiteral()
						.getString();

			}
			sliver.setName(name);
			sliverTypeOrState.add(sliver);
		}

	}

	private void setMonitoringService(Statement resource, NodeContents node) {
		Resource resourceResource = resource.getResource();
		if (resourceResource.hasProperty(Omn_lifecycle.usesService)) {
			Statement monitoringService = resourceResource
					.getProperty(Omn_lifecycle.usesService);
			Resource monitoringResource = monitoringService.getResource();
			Monitoring monitoring = new ObjectFactory().createMonitoring();

			if (monitoringResource.hasProperty(Omn_service.hasURI)) {
				Statement hasUri = monitoringService.getResource().getProperty(
						Omn_service.hasURI);

				System.out.println(hasUri.getObject().asLiteral().getString());
				// String uri = hasUri.getObject().asResource().getURI()
				// .toString();
				String uri = hasUri.getObject().asLiteral().getString();
				monitoring.setUri(uri);

			}

			if (monitoringResource.hasProperty(RDF.type)) {
				Statement hasType = monitoringService.getResource()
						.getProperty(RDF.type);

				// String type = hasType.getObject().asResource().getURI()
				// .toString();
				String type = hasType.getObject().asLiteral().getString();
				monitoring.setType(type);
			}

			node.getAnyOrRelationOrLocation().add(monitoring);
		}

	}

	private void setAvailability(Statement omnResource, NodeContents geniNode) {

		AvailableContents availabilty = of.createAvailableContents();
		Resource resource = omnResource.getResource();

		if (resource.hasProperty(Omn_resource.isAvailable)) {
			availabilty.setNow(resource.getProperty(Omn_resource.isAvailable)
					.getBoolean());

			geniNode.getAnyOrRelationOrLocation().add(
					of.createAvailable(availabilty));
		}
	}

	private void setLocation(Statement omnResource, NodeContents geniNode) {

		LocationContents location = of.createLocationContents();
		Resource omnRes = omnResource.getResource();

		if (omnRes.hasProperty(Omn_resource.country)) {
			location.setCountry(omnRes.getProperty(Omn_resource.country)
					.getString());
		} else {
			// country required
			location.setCountry("");
		}

		if (omnRes.hasProperty(Geo.lat)) {
			location.setLatitude(omnRes.getProperty(Geo.lat).getString());
		}

		if (omnRes.hasProperty(Geo.long_)) {
			location.setLongitude(omnRes.getProperty(Geo.long_).getString());
		}
		if (omnRes.hasProperty(Geo.lat) || omnRes.hasProperty(Geo.long_)) {
			geniNode.getAnyOrRelationOrLocation().add(
					of.createLocation(location));
		}
	}

	private void setSliverTypes(Statement resource, NodeContents geniNode) {

		// StmtIterator canImplement = omnResource.getResource().listProperties(
		// Omn_lifecycle.canImplement);
		// SliverType sliver;
		//
		// List<Object> geniNodeDetails = geniNode.getAnyOrRelationOrLocation();
		// while (canImplement.hasNext()) {
		// Statement omnSliver = canImplement.next();
		// String parentURI = omnSliver.getResource().getURI();
		// sliver = of.createNodeContentsSliverType();
		// sliver.setName(parentURI);
		// if (null != parentURI) {
		// geniNodeDetails.add(of.createNodeContentsSliverType(sliver));
		// RDFNode sliverObject = omnSliver.getObject();
		// Resource sliverResource = sliverObject.asResource();
		// setCpus(sliverResource, sliver);
		// setDiskImage(sliverResource, sliver);
		// }
		// }

		// check if name was string and not uri
		if (resource.getResource().hasProperty(Omn_resource.hasSliverType)) {

			final List<Statement> hasSliverNames = resource.getResource()
					.listProperties(Omn_resource.hasSliverType).toList();

			for (final Statement hasSliverName : hasSliverNames) {

				SliverType sliverType = new ObjectFactory()
						.createNodeContentsSliverType();

				Resource sliverTypeResource = hasSliverName.getObject()
						.asResource();
				if (sliverTypeResource.hasProperty(Omn_lifecycle.hasSliverName)) {
					String sliverName = sliverTypeResource
							.getProperty(Omn_lifecycle.hasSliverName)
							.getObject().asLiteral().getString();
					sliverType.setName(sliverName);
				}

				if (sliverTypeResource != null) {
					setDiskImage(sliverTypeResource, sliverType);
				}

				JAXBElement<SliverType> sliver = new ObjectFactory()
						.createNodeContentsSliverType(sliverType);

				geniNode.getAnyOrRelationOrLocation().add(sliver);
			}
		} else {
			SliverType sliverType = new ObjectFactory()
					.createNodeContentsSliverType();

			final List<Statement> hasTypes = resource.getResource()
					.listProperties(RDF.type).toList();

			for (final Statement hasType : hasTypes) {
				Resource sliverResource = hasType.getObject().asResource();
				if (AbstractConverter.nonGeneric(sliverResource.getURI())) {
					sliverType.setName(sliverResource.getURI());
				}
			}

			JAXBElement<SliverType> sliver = new ObjectFactory()
					.createNodeContentsSliverType(sliverType);
			geniNode.getAnyOrRelationOrLocation().add(sliver);
		}
	}

	private void setCpus(Resource sliverResource, SliverType sliver) {
		Pc pc = null;

		if (sliverResource.hasProperty(Omn_domain_pc.hasCPU)) {
			pc = new ObjectFactory().createPc();
			pc.setCpus(sliverResource.getProperty(Omn_domain_pc.hasCPU)
					.getObject().asLiteral().getInt());
		}
		if (pc != null) {
			sliver.getAnyOrDiskImage().add(pc);
		}

	}

	private void setDiskImage(Resource sliverResource, SliverType sliver) {

		// if (sliverResource.hasProperty(RDFS.subClassOf, Omn_domain_pc.VM)) {
		//
		// StmtIterator omnSliverList = sliverResource
		// .listProperties(Omn_domain_pc.hasDiskImage);
		//
		// while (omnSliverList.hasNext()) {
		// Statement omnSliver = omnSliverList.next();
		// RDFNode diskImageNode = omnSliver.getObject();
		// Resource diskImageResource = diskImageNode.asResource();
		//
		// // check if the resource is a disk image
		// if (diskImageResource.hasProperty(RDF.type,
		// Omn_domain_pc.DiskImage)) {
		// DiskImage diskImage = of
		// .createNodeContentsSliverTypeDiskImage();
		// String diskName = "";
		//
		// if (diskImageResource
		// .hasProperty(Omn_domain_pc.hasDiskimageLabel)) {
		// diskName += diskImageResource
		// .getProperty(Omn_domain_pc.hasDiskimageLabel)
		// .getObject().asLiteral().getString();
		// }
		// diskImage.setName(diskName);
		//
		// if (diskImageResource
		// .hasProperty(Omn_domain_pc.hasDiskimageDescription)) {
		// String description = diskImageResource
		// .getProperty(
		// Omn_domain_pc.hasDiskimageDescription)
		// .getObject().asLiteral().getString();
		// diskImage.setDescription(description);
		// }
		//
		// if (diskImageResource
		// .hasProperty(Omn_domain_pc.diskimageDefault)) {
		// String diskimageDefault = diskImageResource
		// .getProperty(Omn_domain_pc.diskimageDefault)
		// .getObject().asLiteral().getString();
		// diskImage.setDefault(diskimageDefault);
		// }
		//
		// diskImage.setUrl(diskImageResource.getURI());
		// sliver.getAnyOrDiskImage()
		// .add(of.createNodeContentsSliverTypeDiskImage(diskImage));
		// }
		// }

		// } else {
		while (sliverResource.hasProperty(Omn_domain_pc.hasDiskImage)) {
			Statement omnSliver = sliverResource
					.getProperty(Omn_domain_pc.hasDiskImage);
			omnSliver.remove();
			RDFNode diskImageNode = omnSliver.getObject();
			Resource diskImageResource = diskImageNode.asResource();

			// TODO: diskimage is handled in two places. Need to make a
			// single method.
			// check if the resource is a disk image
			if (diskImageResource
					.hasProperty(RDF.type, Omn_domain_pc.DiskImage)) {

				String diskName = "";
				if (diskImageResource
						.hasProperty(Omn_domain_pc.hasDiskimageLabel)) {
					diskName += diskImageResource
							.getProperty(Omn_domain_pc.hasDiskimageLabel)
							.getObject().asLiteral().getString();
				}

				DiskImage diskImage = of
						.createNodeContentsSliverTypeDiskImage();

				if (diskImageResource
						.hasProperty(Omn_domain_pc.hasDiskimageDescription)) {
					String description = diskImageResource
							.getProperty(Omn_domain_pc.hasDiskimageDescription)
							.getObject().asLiteral().getString();
					diskImage.setDescription(description);
				}

				if (diskImageResource.hasProperty(Omn_domain_pc.hasDiskimageOS)) {
					String os = diskImageResource
							.getProperty(Omn_domain_pc.hasDiskimageOS)
							.getObject().asLiteral().getString();
					diskImage.setOs(os);
				}

				if (diskImageResource
						.hasProperty(Omn_domain_pc.hasDiskimageVersion)) {
					String version = diskImageResource
							.getProperty(Omn_domain_pc.hasDiskimageVersion)
							.getObject().asLiteral().getString();
					diskImage.setVersion(version);
				}

				if (diskImageResource
						.hasProperty(Omn_domain_pc.hasDiskimageURI)) {
					String uri = diskImageResource
							.getProperty(Omn_domain_pc.hasDiskimageURI)
							.getObject().asLiteral().getString();
					diskImage.setUrl(uri);
				}

				if (diskImageResource
						.hasProperty(Omn_domain_pc.diskimageDefault)) {
					String diskimageDefault = diskImageResource
							.getProperty(Omn_domain_pc.diskimageDefault)
							.getObject().asLiteral().getString();
					diskImage.setDefault(diskimageDefault);
				}

				diskImage.setName(diskName);
				sliver.getAnyOrDiskImage().add(
						of.createNodeContentsSliverTypeDiskImage(diskImage));
			}
		}
		// }
	}

	private void setHardwareTypes(Statement omnResource, NodeContents geniNode) {

		List<Object> geniNodeDetails = geniNode.getAnyOrRelationOrLocation();

		StmtIterator types = omnResource.getResource().listProperties(
				Omn_domain_pc.hasHardwareType);

		// check if the hardware type was specified as a type
		// if (!types.hasNext()) {
		// types = omnResource.getResource().listProperties(RDF.type);
		// HardwareTypeContents hwType;
		//
		// while (types.hasNext()) {
		// String rdfType = types.next().getResource().getURI();
		//
		// hwType = of.createHardwareTypeContents();
		// hwType.setName(rdfType);
		// if ((null != rdfType) && AbstractConverter.nonGeneric(rdfType)) {
		// geniNodeDetails.add(of.createHardwareType(hwType));
		// }
		// }
		// }

		while (types.hasNext()) {
			HardwareTypeContents hwType;
			Resource hwObject = types.next().getObject().asResource();
			String hwName = hwObject.getProperty(RDFS.label).getObject()
					.asLiteral().getString();

			hwType = of.createHardwareTypeContents();
			hwType.setName(hwName);

			// add emulab node slots
			if (hwObject.hasProperty(Omn_domain_pc.hasEmulabNodeTypeSlots)) {
				NodeType nodeType = of.createNodeType();
				// type slots is required
				String numSlots = hwObject
						.getProperty(Omn_domain_pc.hasEmulabNodeTypeSlots)
						.getObject().asLiteral().getString();
				nodeType.setTypeSlots(numSlots);

				if (hwObject.hasProperty(Omn_domain_pc.emulabNodeTypeStatic)) {
					String staticType = hwObject
							.getProperty(Omn_domain_pc.emulabNodeTypeStatic)
							.getObject().asLiteral().getString();
					nodeType.setStatic(staticType);
				}
				hwType.getAny().add(nodeType);
			}

			geniNodeDetails.add(of.createHardwareType(hwType));

		}
	}

	private void setComponentDetails(final Statement resource,
			final NodeContents node) {

		String url = resource.getResource().getURI();
		// String urn = AbstractConverter.generateUrnFromUrl(url, "node");

		node.setComponentId(url);

		if (resource.getResource().hasProperty(Omn_lifecycle.hasComponentName)) {
			node.setComponentName(resource.getResource()
					.getProperty(Omn_lifecycle.hasComponentName).getObject()
					.asLiteral().getString());
		} else {
			node.setComponentName(resource.getResource().getLocalName());
		}

		if (resource.getResource().hasProperty(Omn_resource.isExclusive)) {
			node.setExclusive(resource.getResource()
					.getProperty(Omn_resource.isExclusive).getBoolean());
		}
	}

	private void setComponentManagerId(final Statement resource,
			final NodeContents node) {
		try {
			Statement parentOf = resource.getProperty(Omn_lifecycle.parentOf);
			node.setComponentManagerId(parentOf.getResource().getURI());
		} catch (PropertyNotFoundException e) {
			AdvertisementConverter.LOG.finer(e.getMessage());
		}
	}

	private void setTimeInformation(final RSpecContents manifest) {
		final GregorianCalendar gregorianCalendar = new GregorianCalendar();
		gregorianCalendar.setTime(new Date(System.currentTimeMillis()));
		XMLGregorianCalendar xmlGrogerianCalendar = null;
		try {
			xmlGrogerianCalendar = DatatypeFactory.newInstance()
					.newXMLGregorianCalendar(gregorianCalendar);
		} catch (final DatatypeConfigurationException e) {
			AdvertisementConverter.LOG.info(e.getMessage());
		}
		manifest.setGenerated(xmlGrogerianCalendar);
		manifest.setExpires(xmlGrogerianCalendar);
	}

	public static String toString(JAXBElement<RSpecContents> advertisement)
			throws JAXBException {
		return toString(advertisement, JAXB);
	}

}
