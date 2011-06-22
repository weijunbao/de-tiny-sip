/*
 * This file is part of TinySip. 
 * http://code.google.com/p/de-tiny-sip/
 * 
 * Created 2011 by Sebastian Rösch <flowfire@sebastianroesch.de>
 * 
 * This software is licensed under the Apache License 2.0.
 */

package de.tinysip.sip;

import gov.nist.javax.sdp.MediaDescriptionImpl;
import gov.nist.javax.sdp.fields.AttributeField;
import gov.nist.javax.sdp.fields.ConnectionField;
import gov.nist.javax.sdp.fields.MediaField;
import gov.nist.javax.sdp.fields.OriginField;
import gov.nist.javax.sdp.fields.SDPKeywords;
import gov.nist.javax.sdp.fields.SessionNameField;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import javax.sdp.Attribute;
import javax.sdp.MediaDescription;
import javax.sdp.SdpConstants;
import javax.sdp.SdpException;
import javax.sdp.SdpFactory;
import javax.sdp.SessionDescription;
import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.PeerUnavailableException;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TransportNotSupportedException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import android.util.Log;

import de.javawi.jstun.test.DiscoveryInfo;

/**
 * SipMessageHandler takes care of sending sip messages and parsing incoming ones.
 * 
 * @author Sebastian
 * 
 */
public class SipMessageHandler {
	private static String TAG = "tSIP";
	private AddressFactory addressFactory = null;
	private MessageFactory messageFactory = null;
	private HeaderFactory headerFactory = null;
	private SipStack sipStack = null;
	private SipProvider sipProvider = null;
	private ListeningPoint listeningPoint = null;

	private LocalSipProfile localSipProfile = null;
	private DiscoveryInfo discoveryInfo = null;

	private ServerTransaction serverTransaction = null;
	private ClientTransaction clientTransaction = null;
	private CallIdHeader currentCallID = null;
	private Dialog currentDialog = null;

	private long callSequence = 1L;

	private static SipMessageHandler sipMessageHandler = null;

	/**
	 * Create a SipMessageHandler using the LocalSipProfile and a STUN DiscoveryInfo.
	 * 
	 * @param localSipProfile
	 *            the local user's SIP profile to register with the provider
	 * @param discoveryInfo
	 *            the STUN DiscoveryInfo for NAT traversal
	 * @throws PeerUnavailableException
	 * @throws TransportNotSupportedException
	 * @throws InvalidArgumentException
	 * @throws ObjectInUseException
	 */
	private SipMessageHandler(LocalSipProfile localSipProfile, DiscoveryInfo discoveryInfo) throws PeerUnavailableException, TransportNotSupportedException,
			InvalidArgumentException, ObjectInUseException {
		this.localSipProfile = localSipProfile;
		this.discoveryInfo = discoveryInfo;
		// Create SipFactory
		SipFactory sipFactory = SipFactory.getInstance();

		// Create unique name properties for SipStack
		Properties properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", "ubivoip");

		// Create SipStack object
		sipStack = sipFactory.createSipStack(properties);
		headerFactory = sipFactory.createHeaderFactory();
		addressFactory = sipFactory.createAddressFactory();
		messageFactory = sipFactory.createMessageFactory();

		int minPort = 5000, maxPort = 6000;
		int localPort = discoveryInfo.getLocalPort();
		boolean successfullyBound = false;
		while (!successfullyBound) {
			try {
				listeningPoint = sipStack.createListeningPoint(discoveryInfo.getLocalIP().getHostAddress(), localPort, ListeningPoint.UDP);
			} catch (InvalidArgumentException ex) {
				// choose another port between MIN and MAX
				localPort = (int) ((maxPort - minPort) * Math.random()) + minPort;
				continue;
			}
			successfullyBound = true;
		}

		sipProvider = sipStack.createSipProvider(listeningPoint);
	}

	/**
	 * Create an instance of SipMessageHandler.
	 * 
	 * @param localSipProfile
	 *            the local user's SIP profile to register with the provider
	 * @param discoveryInfo
	 *            the STUN DiscoveryInfo for NAT traversal
	 * @return the created instance of SipMessageHandler
	 * @throws PeerUnavailableException
	 * @throws TransportNotSupportedException
	 * @throws ObjectInUseException
	 * @throws InvalidArgumentException
	 */
	public static SipMessageHandler createInstance(LocalSipProfile localSipProfile, DiscoveryInfo discoveryInfo) throws PeerUnavailableException, TransportNotSupportedException,
			ObjectInUseException, InvalidArgumentException {
		if (sipMessageHandler == null)
			sipMessageHandler = new SipMessageHandler(localSipProfile, discoveryInfo);

		return sipMessageHandler;
	}

	/**
	 * @return the instance of the SipMessageHandler which needs to be created first
	 */
	public static SipMessageHandler getInstance() {
		return sipMessageHandler;
	}

	/**
	 * @return the SipProvider
	 */
	public SipProvider getSipProvider() {
		return sipProvider;
	}

	/**
	 * @return the LocalSipProfile
	 */
	public LocalSipProfile getLocalSipProfile() {
		return localSipProfile;
	}

	/**
	 * Register the local profile with the provider.
	 * 
	 * @param state
	 *            the current SipRequestState of the SipManager, used for assembling the message
	 * @throws ParseException
	 * @throws InvalidArgumentException
	 * @throws SipException
	 */
	public void register(SipRequestState state) throws ParseException, InvalidArgumentException, SipException {
		// create To and From headers
		FromHeader fromHeader = localSipProfile.getFromHeader(addressFactory, headerFactory);
		ToHeader toHeader = localSipProfile.getToHeader(addressFactory, headerFactory);

		// create a new Request URI
		SipURI requestURI = addressFactory.createSipURI(localSipProfile.getUserName(), localSipProfile.getSipDomain());

		// Create Via headers
		List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader viaHeader = headerFactory.createViaHeader(discoveryInfo.getPublicIP().getHostAddress(), discoveryInfo.getPublicPort(), listeningPoint.getTransport(), null);
		viaHeader.setRPort();
		viaHeaders.add(viaHeader);

		// Create a new CallId header
		CallIdHeader callIdHeader = sipProvider.getNewCallId();

		// Create a new Cseq header
		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(callSequence++, Request.REGISTER);

		// Create a new MaxForwards header
		MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

		// Create the request
		Request request = messageFactory.createRequest(requestURI, Request.REGISTER, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);

		if (state.equals(SipRequestState.UNREGISTER) || state.equals(SipRequestState.UNREGISTER_AUTHORIZATION)) {
			// Create a new Expires header
			ExpiresHeader expires = headerFactory.createExpiresHeader(0);
			request.addHeader(expires);

			// Create an empty Contact header
			ContactHeader contactHeader = headerFactory.createContactHeader();
			request.addHeader(contactHeader);
		} else {
			// Create the contact name address
			SipURI contactURI = addressFactory.createSipURI(localSipProfile.getUserName(), discoveryInfo.getPublicIP().getHostAddress());
			contactURI.setPort(discoveryInfo.getPublicPort());
			Address contactAddress = addressFactory.createAddress(contactURI);
			contactAddress.setDisplayName(localSipProfile.getDisplayName());

			// Create a new Contact header
			ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
			contactHeader.setExpires(3600); // 20 minutes
			request.addHeader(contactHeader);
		}

		if (state.equals(SipRequestState.AUTHORIZATION) || state.equals(SipRequestState.UNREGISTER_AUTHORIZATION)) {
			request.addHeader(localSipProfile.getAuthorizationHeader(headerFactory));
		}

		// Create the client transaction and send the request
		clientTransaction = sipProvider.getNewClientTransaction(request);
		clientTransaction.sendRequest();
		Log.d(TAG, "REGISTER sent");
	}

	/**
	 * Creates a SipSession.
	 * 
	 * @param message
	 *            the message to extract the SipSession information
	 * @param sdpSession
	 *            a SessionDescription to parse supported formats
	 * @return a SipSession containing all information about the current session
	 * @throws SdpException
	 * @throws UnknownHostException
	 */
	public static SipSession createSipSession(Message message, SessionDescription sdpSession) throws SdpException, UnknownHostException {
		FromHeader fromHeader = (FromHeader) message.getHeader(FromHeader.NAME);
		ToHeader toHeader = (ToHeader) message.getHeader(ToHeader.NAME);
		ContactHeader contactHeader = (ContactHeader) message.getHeader(ContactHeader.NAME);
		String address = contactHeader.getAddress().getURI().toString().split("@")[1].split(";")[0];

		List<SipAudioFormat> formatList = null;
		int rtpPort = 0, rtcpPort = 0;

		@SuppressWarnings("unchecked")
		Vector<MediaDescription> v = sdpSession.getMediaDescriptions(false);
		if (v != null) {
			for (MediaDescription item : v) {
				if (item.getMedia().getMediaType().contains("audio")) {
					rtpPort = item.getMedia().getMediaPort();

					@SuppressWarnings("unchecked")
					Vector<Attribute> formats = item.getAttributes(false);
					formatList = new LinkedList<SipAudioFormat>();
					if (formats != null) {
						for (Attribute attribute : formats) {
							if (attribute.getName() != null && attribute.getName().contains("rtpmap")) {
								try {
									formatList.add(SipAudioFormat.parseAudioFormat(attribute.getValue()));
								} catch (Exception e) {
									// not a valid number
								}
							} else if (attribute.getName().contains("rtcp")) {
								try {
									rtcpPort = Integer.parseInt(attribute.getValue());
								} catch (Exception e) {
									// not a valid number
								}
							}
						}
					}
				} else if (item.getMedia().getMediaType().contains("audio")) {
					// TODO: implement support for video types
				}
			}
		}

		SipSession session;
		if (address.contains(":")) {
			int port = Integer.parseInt(address.split(":")[1]);
			InetAddress inetAddress = InetAddress.getByName(sdpSession.getConnection().getAddress());

			session = new SipSession(fromHeader.getAddress().getURI(), toHeader.getAddress().getURI(), inetAddress, port, rtpPort, rtcpPort);
		} else {
			InetAddress inetAddress = InetAddress.getByName(sdpSession.getConnection().getAddress());

			session = new SipSession(fromHeader.getAddress().getURI(), toHeader.getAddress().getURI(), inetAddress, 5060, rtpPort, rtcpPort);
		}
		session.setAudioFormats(formatList);

		Log.d(TAG, "SipSession created: " + session.toString());
		return session;
	}

	/**
	 * Send a sip ringing reply.
	 * 
	 * @param invite
	 *            the Request to reply to
	 * @throws SipException
	 * @throws InvalidArgumentException
	 * @throws ParseException
	 */
	public void sendRinging(Request invite) throws SipException, InvalidArgumentException, ParseException {
		// Create a new response for the request
		Response response = messageFactory.createResponse(Response.RINGING, invite);

		// Send the created response
		if (serverTransaction == null)
			serverTransaction = sipProvider.getNewServerTransaction(invite);
		serverTransaction.sendResponse(response);
		Log.d(TAG, "RINGING sent");
	}

	/**
	 * Send a sip ack reply.
	 * 
	 * @throws InvalidArgumentException
	 * @throws SipException
	 */
	public void sendAck() throws InvalidArgumentException, SipException {
		Request request = currentDialog.createAck(callSequence);
		currentDialog.sendAck(request);
		Log.d(TAG, "ACK sent");
	}

	/**
	 * Send a sip ok reply.
	 * 
	 * @param request
	 *            the Request to reply to
	 * @param transaction
	 *            the ServerTransaction used for creating the reply
	 * @throws SipException
	 * @throws InvalidArgumentException
	 * @throws ParseException
	 * @throws SdpException
	 */
	public void sendOK(Request request, ServerTransaction transaction) throws SipException, InvalidArgumentException, ParseException, SdpException {
		// Create a new response for the request
		Response response = messageFactory.createResponse(Response.OK, request);

		if (transaction == null) {
			// Create the contact name address
			SipURI contactURI = addressFactory.createSipURI(localSipProfile.getUserName(), discoveryInfo.getPublicIP().getHostAddress());
			contactURI.setPort(discoveryInfo.getPublicPort());
			Address contactAddress = addressFactory.createAddress(contactURI);
			contactAddress.setDisplayName(localSipProfile.getDisplayName());

			// Create a new Contact header
			ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
			contactHeader.setExpires(3600); // 20 minutes
			response.addHeader(contactHeader);

			ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
			SessionDescription sdp = createSDP(request);

			response.setContent(sdp.toString(), contentTypeHeader);

			// Send the created response
			if (serverTransaction == null)
				serverTransaction = sipProvider.getNewServerTransaction(request);
			serverTransaction.sendResponse(response);
		} else {
			transaction.sendResponse(response);
		}
		Log.d(TAG, "OK sent");
	}

	/**
	 * Set the current sip dialog.
	 * 
	 * @param dialog
	 *            the SipManager's current dialog
	 */
	public void setDialog(Dialog dialog) {
		this.currentDialog = dialog;
	}

	/**
	 * Send a sip bye request.
	 * 
	 * @throws ParseException
	 * @throws InvalidArgumentException
	 * @throws SipException
	 */
	public void sendBye() throws ParseException, InvalidArgumentException, SipException {
		// create Request from dialog
		Request request = currentDialog.createRequest(Request.BYE);

		// Create the client transaction and send the request
		ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(request);
		currentDialog.sendRequest(clientTransaction);
		Log.d(TAG, "BYE sent");
	}

	/**
	 * Send a sip cancel request.
	 * 
	 * @throws ParseException
	 * @throws InvalidArgumentException
	 * @throws SipException
	 */
	public void sendCancel() throws ParseException, InvalidArgumentException, SipException {
		// create Request from dialog
		Request request = clientTransaction.createCancel();

		// Create the client transaction and send the request
		clientTransaction = sipProvider.getNewClientTransaction(request);
		clientTransaction.sendRequest();
		Log.d(TAG, "CANCEL sent");
	}

	/**
	 * Send a sip invite request.
	 * 
	 * @param contact
	 *            the SipContact to call
	 * @param state
	 *            the SipRequestState of the SipManager
	 * @throws ParseException
	 * @throws InvalidArgumentException
	 * @throws NullPointerException
	 * @throws SipException
	 * @throws SdpException
	 */
	public void sendInvite(SipContact contact, SipRequestState state) throws ParseException, InvalidArgumentException, NullPointerException, SipException, SdpException {
		// create To and From headers
		FromHeader fromHeader = localSipProfile.getFromHeader(addressFactory, headerFactory);
		ToHeader toHeader = contact.getToHeader(addressFactory, headerFactory);

		// create a new Request URI
		SipURI requestURI = addressFactory.createSipURI(contact.getSipUserName(), contact.getSipDomain());

		// Create Via headers
		List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader viaHeader = headerFactory.createViaHeader(discoveryInfo.getPublicIP().getHostAddress(), discoveryInfo.getPublicPort(), listeningPoint.getTransport(), null);
		viaHeader.setRPort();
		viaHeaders.add(viaHeader);

		// Create a new CallId header
		if (state.equals(SipRequestState.REGISTER))
			currentCallID = sipProvider.getNewCallId();

		// Create a new Cseq header
		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(callSequence++, Request.INVITE);

		// Create a new MaxForwards header
		MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

		// Create the request
		Request request = messageFactory.createRequest(requestURI, Request.INVITE, currentCallID, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);

		// Create a new Expires header
		ExpiresHeader expires = headerFactory.createExpiresHeader(120);
		request.addFirst(expires);

		// Create the contact name address
		SipURI contactURI = addressFactory.createSipURI(localSipProfile.getUserName(), discoveryInfo.getPublicIP().getHostAddress());
		contactURI.setPort(discoveryInfo.getPublicPort());
		Address contactAddress = addressFactory.createAddress(contactURI);
		contactAddress.setDisplayName(localSipProfile.getDisplayName());

		// Create a new Contact header
		ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
		request.addHeader(contactHeader);

		if (state.equals(SipRequestState.AUTHORIZATION)) {
			request.addHeader(localSipProfile.getProxyAuthorizationHeader(headerFactory, contact));
		}

		ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
		SessionDescription sdp = createSDP(null);
		request.setContent(sdp, contentTypeHeader);

		// Create the client transaction and send the request
		clientTransaction = sipProvider.getNewClientTransaction(request);
		clientTransaction.sendRequest();

		currentDialog = clientTransaction.getDialog();
		Log.d(TAG, "INVITE sent");
	}

	/**
	 * Send a sip decline response.
	 * 
	 * @param invite
	 *            the Request to reply to
	 * @throws ParseException
	 * @throws SipException
	 * @throws InvalidArgumentException
	 */
	public void sendDecline(Request invite) throws ParseException, SipException, InvalidArgumentException {
		// Create a new response for the request
		Response response = messageFactory.createResponse(Response.DECLINE, invite);

		// Send the created response
		if (serverTransaction == null)
			serverTransaction = sipProvider.getNewServerTransaction(invite);
		serverTransaction.sendResponse(response);
		Log.d(TAG, "DECLINE sent");
	}

	/**
	 * Send a sip busy here response.
	 * 
	 * @param invite
	 *            the Request to reply to
	 * @throws ParseException
	 * @throws SipException
	 * @throws InvalidArgumentException
	 */
	public void sendBusyHere(Request invite) throws ParseException, SipException, InvalidArgumentException {
		// Create a new response for the request
		Response response = messageFactory.createResponse(Response.BUSY_HERE, invite);

		// Send the created response
		if (serverTransaction == null)
			serverTransaction = sipProvider.getNewServerTransaction(invite);
		serverTransaction.sendResponse(response);
		Log.d(TAG, "BUSY HERE sent");
	}

	/**
	 * Create a session description for the local profile.
	 * 
	 * @param request
	 *            the Request to extract the SessionDescription from
	 * @return the SessionDescription for the local profile
	 * @throws SdpException
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private SessionDescription createSDP(Request request) throws SdpException {
		SessionDescription sdp = SdpFactory.getInstance().createSessionDescription();
		long sessionId = 0L, sessionVersion = 0L;
		String sessionName;
		if (request == null) {
			sessionId = (long) Math.random() * 100000000L;
			sessionVersion = sessionId;
			sessionName = "call";
		} else {
			SessionDescription sdpSession = SdpFactory.getInstance().createSessionDescription(new String(request.getRawContent()));
			sessionId = sdpSession.getOrigin().getSessionId();
			sessionVersion = sdpSession.getOrigin().getSessionVersion();
			sessionName = sdpSession.getSessionName().getValue();
		}

		OriginField originField = new OriginField();
		originField.setUsername(localSipProfile.getDisplayName());
		originField.setSessionId(sessionId);
		originField.setSessVersion(sessionVersion);
		originField.setNetworkType(SDPKeywords.IN);
		originField.setAddressType(SDPKeywords.IPV4);
		originField.setAddress(discoveryInfo.getPublicIP().getHostAddress());

		SessionNameField sessionNameField = new SessionNameField();
		sessionNameField.setSessionName(sessionName);

		ConnectionField connectionField = new ConnectionField();
		connectionField.setNetworkType(SDPKeywords.IN);
		connectionField.setAddressType(SDPKeywords.IPV4);
		connectionField.setAddress(discoveryInfo.getPublicIP().getHostAddress());

		Vector mediaDescriptions = new Vector();
		MediaField mediaField = new MediaField();
		mediaField.setMediaType("audio");
		mediaField.setPort(localSipProfile.getLocalRtpPort());
		mediaField.setProtocol(SdpConstants.RTP_AVP);

		Vector<String> mediaFormats = new Vector<String>();
		for (SipAudioFormat audioFormat : localSipProfile.getAudioFormats()) {
			mediaFormats.add(audioFormat.getFormat() + "");
		}
		mediaField.setMediaFormats(mediaFormats);

		MediaDescriptionImpl mediaDescription = new MediaDescriptionImpl();

		for (SipAudioFormat audioFormat : localSipProfile.getAudioFormats()) {
			AttributeField attributeField = new AttributeField();
			attributeField.setName(SdpConstants.RTPMAP);
			attributeField.setValue(audioFormat.getSdpField());
			mediaDescription.addAttribute(attributeField);
		}

		AttributeField sendReceive = new AttributeField();
		sendReceive.setValue("sendrecv");
		mediaDescription.addAttribute(sendReceive);

		AttributeField rtcpAttribute = new AttributeField();
		rtcpAttribute.setName("rtcp");
		rtcpAttribute.setValue(localSipProfile.getLocalRtcpPort() + "");
		mediaDescription.addAttribute(rtcpAttribute);

		mediaDescriptions.add(mediaField);
		mediaDescriptions.add(mediaDescription);

		sdp.setOrigin(originField);
		sdp.setSessionName(sessionNameField);
		sdp.setConnection(connectionField);
		sdp.setMediaDescriptions(mediaDescriptions);

		return sdp;
	}

	/**
	 * Reset all current data.
	 */
	public void reset() {
		serverTransaction = null;
		clientTransaction = null;
		currentDialog = null;
		currentCallID = null;
		callSequence = 1L;
	}
}
