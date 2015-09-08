package info.openmultinet.ontology.translators.geni;

import info.openmultinet.ontology.translators.AbstractConverter;
import info.openmultinet.ontology.Parser;
import info.openmultinet.ontology.exceptions.InvalidModelException;
import info.openmultinet.ontology.translators.AbstractConverter;
import info.openmultinet.ontology.translators.geni.jaxb.advertisement.RSpecContents;
import info.openmultinet.ontology.vocabulary.Omn_lifecycle;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.Difference;
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier;
import org.custommonkey.xmlunit.Validator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;

public class CommonMethods {

	public static String generateUrnFromUrl(String url, String type) {
		// http://groups.geni.net/geni/wiki/GeniApiIdentifiers
		// urn:publicid:IDN+<authority string>+<type>+<name>
		// type can be interface, link or node

		if (url == null) {
			return "";
		}

		URI uri = URI.create(url);
		if (uri.getScheme() == null) {
			return "";
		}

		if (uri.getScheme().equals("http") || uri.getScheme().equals("https")) {

			// AbstractConverter.LOG.info(uri.getScheme() + ": " +
			// uri.toString());

			String urn = "";
			String host = urlToGeniUrn(uri.getHost());
			String path = urlToGeniUrn(uri.getPath());
			String fragment = urlToGeniUrn(uri.getFragment());
			String scheme = urlToGeniUrn(uri.getScheme());

			urn = "urn:publicid:IDN+" + host + "+" + urlToGeniUrn(type) + "+"
					+ scheme + "%3A%2F%2F" + host + path;

			if (fragment != null && !fragment.equals("")) {
				urn += "%23" + fragment;
			}

			return urn;
		} else {

			return url;
		}

	}

	private static String urlToGeniUrn(String dirtyString) {

		// http://groups.geni.net/geni/wiki/GeniApiIdentifiers
		// From Transcribe to
		// leading and trailing whitespace trim
		// whitespace collapse to a single '+'
		// '//' ':'
		// '::' ';'
		// '+' '%2B'
		// ":' '%3A'
		// '/' '%2F'
		// ';' '%3B'
		// ''' '%27'
		// '?' '%3F'
		// '#' '%23'
		// '%' '%25

		if (dirtyString == null) {
			return "";
		}
		String cleanString;
		cleanString = dirtyString.replaceAll(";", "%3B");
		cleanString = cleanString.replaceAll("%", "%25");
		cleanString = cleanString.replaceAll(":", "%3A");
		cleanString = cleanString.replaceAll("\\+", "%2B");
		cleanString = cleanString.replaceAll("//", ":");
		cleanString = cleanString.replaceAll("::", ";");
		cleanString = cleanString.replaceAll("/", "%2F");
		cleanString = cleanString.replaceAll("'", "%27");
		cleanString = cleanString.replaceAll("\\?", "%3F");
		cleanString = cleanString.replaceAll("#", "%23");
		cleanString = cleanString.trim();
		cleanString = cleanString.replaceAll("\\s+", "+");

		return cleanString;
	}

	public static String generateUrlFromUrn(String urn) {

		if (urn == null) {
			return "";
		}

		URI uri = URI.create(urn);
		if (uri == null) {
			return "";
		}

		if (uri.getScheme().equals("urn")) {

			String url = "";
			String[] parts = urn.split("\\+");

			if (parts.length > 1) {
				if (parts.length > 3) {
					if (AbstractConverter.isUrl(geniUrntoUrl(parts[3]))) {
						String http = geniUrntoUrl(parts[3]);
						url += http;
					} else {
						return urn;
					}
				}
			}
			return url;
		} else {
			return urn;
		}
	}

	private static String geniUrntoUrl(String dirtyString) {

		if (dirtyString == null) {
			return "";
		}
		String cleanString;

		cleanString = dirtyString.replaceAll("\\+", " ");
		cleanString = cleanString.replaceAll("%23", "#");
		cleanString = cleanString.replaceAll("%3F", "?");
		cleanString = cleanString.replaceAll("%27", "'");
		cleanString = cleanString.replaceAll("%2F", "/");
		cleanString = cleanString.replaceAll(";", "::");
		cleanString = cleanString.replaceAll(":", "//");
		cleanString = cleanString.replaceAll("%2B", "+");
		cleanString = cleanString.replaceAll("%3A", ":");
		cleanString = cleanString.replaceAll("%25", "%");
		cleanString = cleanString.replaceAll("%3B", ";");

		return cleanString;
	}

	static OntClass convertGeniStateToOmn(String geniState) {

		OntClass omnState = null;
		switch (geniState) {
		case "geni_ready_busy":
			omnState = Omn_lifecycle.Active;
			break;
		case "ready_busy":
			omnState = Omn_lifecycle.Active;
			break;
		case "geni_allocated":
			omnState = Omn_lifecycle.Allocated;
			break;
		case "allocated":
			omnState = Omn_lifecycle.Allocated;
			break;
		case "geni_configuring":
			omnState = Omn_lifecycle.Preinit;
			break;
		case "configuring":
			omnState = Omn_lifecycle.Preinit;
			break;
		case "geni_failed":
			omnState = Omn_lifecycle.Error;
			break;
		case "failed":
			omnState = Omn_lifecycle.Error;
			break;
		case "geni_failure":
			omnState = Omn_lifecycle.Failure;
			break;
		case "Nascent":
			omnState = Omn_lifecycle.Nascent;
			break;
		case "geni_instantiating":
			omnState = Omn_lifecycle.NotYetInitialized;
			break;
		case "instantiating":
			omnState = Omn_lifecycle.NotYetInitialized;
			break;
		case "geni_notready":
			omnState = Omn_lifecycle.NotReady;
			break;
		case "geni_pending_allocation":
			omnState = Omn_lifecycle.Pending;
			break;
		case "pending_allocation":
			omnState = Omn_lifecycle.Pending;
			break;
		case "geni_provisioned":
			omnState = Omn_lifecycle.Provisioned;
			break;
		case "provisioned":
			omnState = Omn_lifecycle.Provisioned;
			break;
		case "geni_ready":
			omnState = Omn_lifecycle.Ready;
			break;
		case "ready":
			omnState = Omn_lifecycle.Ready;
			break;
		case "geni_reload":
			omnState = Omn_lifecycle.Reload;
			break;
		case "geni_restart":
			omnState = Omn_lifecycle.Restart;
			break;
		case "geni_start":
			omnState = Omn_lifecycle.Start;
			break;
		case "geni_stop":
			omnState = Omn_lifecycle.Stop;
			break;
		case "geni_stopping":
			omnState = Omn_lifecycle.Stopping;
			break;
		case "stopping":
			omnState = Omn_lifecycle.Stopping;
			break;
		case "geni_success":
			omnState = Omn_lifecycle.Success;
			break;
		case "geni_update_users":
			omnState = Omn_lifecycle.UpdateUsers;
			break;
		case "geni_update_users_cancel":
			omnState = Omn_lifecycle.UpdateUsersCancel;
			break;
		case "geni_updating_users":
			omnState = Omn_lifecycle.UpdatingUsers;
			break;
		case "geni_unallocated":
			omnState = Omn_lifecycle.Unallocated;
			break;
		case "unallocated":
			omnState = Omn_lifecycle.Unallocated;
			break;
		}

		return omnState;
	}

	static String convertOmnToGeniState(Resource start) {

		String geniState = "";

		if (start.equals(Omn_lifecycle.Active)) {
			geniState = "geni_ready_busy";
		} else if (start.equals(Omn_lifecycle.Allocated)) {
			geniState = "geni_allocated";
		} else if (start.equals(Omn_lifecycle.Error)) {
			geniState = "geni_failed";
		} else if (start.equals(Omn_lifecycle.Failure)) {
			geniState = "geni_failure";
		} else if (start.equals(Omn_lifecycle.Nascent)) {
			geniState = "Nascent";
		} else if (start.equals(Omn_lifecycle.NotReady)) {
			geniState = "geni_notready";
		} else if (start.equals(Omn_lifecycle.NotYetInitialized)) {
			geniState = "geni_instantiating";
		} else if (start.equals(Omn_lifecycle.Pending)) {
			geniState = "geni_pending_allocation";
		} else if (start.equals(Omn_lifecycle.Preinit)) {
			geniState = "geni_configuring";
		} else if (start.equals(Omn_lifecycle.Provisioned)) {
			geniState = "geni_provisioned";
		} else if (start.equals(Omn_lifecycle.Ready)) {
			geniState = "geni_ready";
		} else if (start.equals(Omn_lifecycle.Reload)) {
			geniState = "geni_reload";
		} else if (start.equals(Omn_lifecycle.Restart)) {
			geniState = "geni_restart";
		} else if (start.equals(Omn_lifecycle.Start)) {
			geniState = "geni_start";
		} else if (start.equals(Omn_lifecycle.Success)) {
			geniState = "geni_success";
		} else if (start.equals(Omn_lifecycle.Stop)) {
			geniState = "geni_stop";
		} else if (start.equals(Omn_lifecycle.Stopping)) {
			geniState = "geni_stopping";
		} else if (start.equals(Omn_lifecycle.Unallocated)) {
			geniState = "geni_unallocated";
		} else if (start.equals(Omn_lifecycle.UpdateUsers)) {
			geniState = "geni_update_users";
		} else if (start.equals(Omn_lifecycle.UpdateUsersCancel)) {
			geniState = "geni_update_users_cancel";
		} else if (start.equals(Omn_lifecycle.UpdatingUsers)) {
			geniState = "geni_updating_users";
		}

		return geniState;

	}

	static boolean isOmnState(Resource type) {

		boolean geniState = false;

		if (type.equals(Omn_lifecycle.Active)
				|| type.equals(Omn_lifecycle.Allocated)
				|| type.equals(Omn_lifecycle.Error)
				|| type.equals(Omn_lifecycle.Failure)
				|| type.equals(Omn_lifecycle.Nascent)
				|| type.equals(Omn_lifecycle.NotReady)
				|| type.equals(Omn_lifecycle.NotYetInitialized)
				|| type.equals(Omn_lifecycle.Pending)
				|| type.equals(Omn_lifecycle.Preinit)
				|| type.equals(Omn_lifecycle.Provisioned)
				|| type.equals(Omn_lifecycle.Ready)
				|| type.equals(Omn_lifecycle.Reload)
				|| type.equals(Omn_lifecycle.Restart)
				|| type.equals(Omn_lifecycle.Start)
				|| type.equals(Omn_lifecycle.Stop)
				|| type.equals(Omn_lifecycle.Stopping)
				|| type.equals(Omn_lifecycle.Success)
				|| type.equals(Omn_lifecycle.UpdateUsers)
				|| type.equals(Omn_lifecycle.UpdateUsersCancel)
				|| type.equals(Omn_lifecycle.UpdatingUsers)
				|| type.equals(Omn_lifecycle.Unallocated)) {
			geniState = true;
		}

		return geniState;

	}

}