package de.tengu.chat.services;

import android.util.Log;
import android.util.Pair;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import de.tengu.chat.Config;
import de.tengu.chat.R;
import de.tengu.chat.entities.Account;
import de.tengu.chat.entities.Conversation;
import de.tengu.chat.generator.AbstractGenerator;
import de.tengu.chat.xml.Namespace;
import de.tengu.chat.xml.Element;
import de.tengu.chat.xmpp.OnAdvancedStreamFeaturesLoaded;
import de.tengu.chat.xmpp.OnIqPacketReceived;
import de.tengu.chat.xmpp.jid.Jid;
import de.tengu.chat.xmpp.stanzas.IqPacket;

public class MessageArchiveService implements OnAdvancedStreamFeaturesLoaded {

	private final XmppConnectionService mXmppConnectionService;

	private final HashSet<Query> queries = new HashSet<>();
	private final ArrayList<Query> pendingQueries = new ArrayList<>();

	public enum PagingOrder {
		NORMAL,
		REVERSE
	}

	public MessageArchiveService(final XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	private void catchup(final Account account) {
		synchronized (this.queries) {
			for(Iterator<Query> iterator = this.queries.iterator(); iterator.hasNext();) {
				Query query = iterator.next();
				if (query.getAccount() == account) {
					iterator.remove();
				}
			}
		}
		final Pair<Long,String> lastMessageReceived = mXmppConnectionService.databaseBackend.getLastMessageReceived(account);
		final Pair<Long,String> lastClearDate = mXmppConnectionService.databaseBackend.getLastClearDate(account);
		long startCatchup;
		final String reference;
		if (lastMessageReceived != null && lastMessageReceived.first >= lastClearDate.first) {
			startCatchup = lastMessageReceived.first;
			reference = lastMessageReceived.second;
		} else {
			startCatchup = lastClearDate.first;
			reference = null;
		}
		startCatchup = Math.max(startCatchup,mXmppConnectionService.getAutomaticMessageDeletionDate());
		long endCatchup = account.getXmppConnection().getLastSessionEstablished();
		final Query query;
		if (startCatchup == 0) {
			return;
		} else if (endCatchup - startCatchup >= Config.MAM_MAX_CATCHUP) {
			startCatchup = endCatchup - Config.MAM_MAX_CATCHUP;
			List<Conversation> conversations = mXmppConnectionService.getConversations();
			for (Conversation conversation : conversations) {
				if (conversation.getMode() == Conversation.MODE_SINGLE && conversation.getAccount() == account && startCatchup > conversation.getLastMessageTransmitted()) {
					this.query(conversation,startCatchup);
				}
			}
			query = new Query(account, startCatchup, endCatchup);
		} else {
			query = new Query(account, startCatchup, endCatchup);
			query.reference = reference;
		}
		this.queries.add(query);
		this.execute(query);
	}

	public void catchupMUC(final Conversation conversation) {
		if (conversation.getLastMessageTransmitted() < 0 && conversation.countMessages() == 0) {
			query(conversation,
					0,
					System.currentTimeMillis());
		} else {
			query(conversation,
					conversation.getLastMessageTransmitted(),
					System.currentTimeMillis());
		}
	}

	public Query query(final Conversation conversation) {
		if (conversation.getLastMessageTransmitted() < 0 && conversation.countMessages() == 0) {
			return query(conversation,
					0,
					System.currentTimeMillis());
		} else {
			return query(conversation,
					conversation.getLastMessageTransmitted(),
					conversation.getAccount().getXmppConnection().getLastSessionEstablished());
		}
	}

	public Query query(final Conversation conversation, long end) {
		return this.query(conversation,conversation.getLastMessageTransmitted(),end);
	}

	public Query query(Conversation conversation, long start, long end) {
		synchronized (this.queries) {
			final Query query;
			final long startActual = Math.max(start,mXmppConnectionService.getAutomaticMessageDeletionDate());
			if (start==0) {
				query = new Query(conversation, startActual, end, false);
				query.reference = conversation.getFirstMamReference();
			} else {
				long maxCatchup = Math.max(startActual,System.currentTimeMillis() - Config.MAM_MAX_CATCHUP);
				if (maxCatchup > startActual) {
					Query reverseCatchup = new Query(conversation,startActual,maxCatchup,false);
					this.queries.add(reverseCatchup);
					this.execute(reverseCatchup);
				}
				query = new Query(conversation, maxCatchup, end);
			}
			if (start > end) {
				return null;
			}
			this.queries.add(query);
			this.execute(query);
			return query;
		}
	}

	public void executePendingQueries(final Account account) {
		List<Query> pending = new ArrayList<>();
		synchronized(this.pendingQueries) {
			for(Iterator<Query> iterator = this.pendingQueries.iterator(); iterator.hasNext();) {
				Query query = iterator.next();
				if (query.getAccount() == account) {
					pending.add(query);
					iterator.remove();
				}
			}
		}
		for(Query query : pending) {
			this.execute(query);
		}
	}

	private void execute(final Query query) {
		final Account account=  query.getAccount();
		if (account.getStatus() == Account.State.ONLINE) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": running mam query " + query.toString());
			IqPacket packet = this.mXmppConnectionService.getIqGenerator().queryMessageArchiveManagement(query);
			this.mXmppConnectionService.sendIqPacket(account, packet, new OnIqPacketReceived() {
				@Override
				public void onIqPacketReceived(Account account, IqPacket packet) {
					Element fin = packet.findChild("fin", Namespace.MAM);
					if (packet.getType() == IqPacket.TYPE.TIMEOUT) {
						synchronized (MessageArchiveService.this.queries) {
							MessageArchiveService.this.queries.remove(query);
							if (query.hasCallback()) {
								query.callback(false);
							}
						}
					} else if (packet.getType() == IqPacket.TYPE.RESULT && fin != null ) {
						processFin(fin);
					} else if (packet.getType() == IqPacket.TYPE.RESULT && query.isLegacy()) {
						//do nothing
					} else {
						Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": error executing mam: " + packet.toString());
						finalizeQuery(query, true);
					}
				}
			});
		} else {
			synchronized (this.pendingQueries) {
				this.pendingQueries.add(query);
			}
		}
	}

	private void finalizeQuery(Query query, boolean done) {
		synchronized (this.queries) {
			this.queries.remove(query);
		}
		final Conversation conversation = query.getConversation();
		if (conversation != null) {
			conversation.sort();
			conversation.setHasMessagesLeftOnServer(!done);
		} else {
			for(Conversation tmp : this.mXmppConnectionService.getConversations()) {
				if (tmp.getAccount() == query.getAccount()) {
					tmp.sort();
				}
			}
		}
		if (query.hasCallback()) {
			query.callback(done);
		} else {
			this.mXmppConnectionService.updateConversationUi();
		}
	}

	public boolean queryInProgress(Conversation conversation, XmppConnectionService.OnMoreMessagesLoaded callback) {
		synchronized (this.queries) {
			for(Query query : queries) {
				if (query.conversation == conversation) {
					if (!query.hasCallback() && callback != null) {
						query.setCallback(callback);
					}
					return true;
				}
			}
			return false;
		}
	}

	public boolean queryInProgress(Conversation conversation) {
		return queryInProgress(conversation, null);
	}

	public void processFinLegacy(Element fin, Jid from) {
		Query query = findQuery(fin.getAttribute("queryid"));
		if (query != null && query.validFrom(from)) {
			processFin(fin);
		}
	}

	public void processFin(Element fin) {
		Query query = findQuery(fin.getAttribute("queryid"));
		if (query == null) {
			return;
		}
		boolean complete = fin.getAttributeAsBoolean("complete");
		Element set = fin.findChild("set","http://jabber.org/protocol/rsm");
		Element last = set == null ? null : set.findChild("last");
		Element first = set == null ? null : set.findChild("first");
		Element relevant = query.getPagingOrder() == PagingOrder.NORMAL ? last : first;
		boolean abort = (!query.isCatchup() && query.getTotalCount() >= Config.PAGE_SIZE) || query.getTotalCount() >= Config.MAM_MAX_MESSAGES;
		if (query.getConversation() != null) {
			query.getConversation().setFirstMamReference(first == null ? null : first.getContent());
		}
		if (complete || relevant == null || abort) {
			final boolean done = (complete || query.getActualMessageCount() == 0) && !query.isCatchup();
			this.finalizeQuery(query, done);
			Log.d(Config.LOGTAG,query.getAccount().getJid().toBareJid()+": finished mam after "+query.getTotalCount()+"("+query.getActualMessageCount()+") messages. messages left="+Boolean.toString(!done));
			if (query.isCatchup() && query.getActualMessageCount() > 0) {
				mXmppConnectionService.getNotificationService().finishBacklog(true,query.getAccount());
			}
		} else {
			final Query nextQuery;
			if (query.getPagingOrder() == PagingOrder.NORMAL) {
				nextQuery = query.next(last == null ? null : last.getContent());
			} else {
				nextQuery = query.prev(first == null ? null : first.getContent());
			}
			this.execute(nextQuery);
			this.finalizeQuery(query, false);
			synchronized (this.queries) {
				this.queries.add(nextQuery);
			}
		}
	}

	public Query findQuery(String id) {
		if (id == null) {
			return null;
		}
		synchronized (this.queries) {
			for(Query query : this.queries) {
				if (query.getQueryId().equals(id)) {
					return query;
				}
			}
			return null;
		}
	}

	@Override
	public void onAdvancedStreamFeaturesAvailable(Account account) {
		if (account.getXmppConnection() != null && account.getXmppConnection().getFeatures().mam()) {
			this.catchup(account);
		}
	}

	public class Query {
		private int totalCount = 0;
		private int actualCount = 0;
		private long start;
		private long end;
		private String queryId;
		private String reference = null;
		private Account account;
		private Conversation conversation;
		private PagingOrder pagingOrder = PagingOrder.NORMAL;
		private XmppConnectionService.OnMoreMessagesLoaded callback = null;
		private boolean catchup = true;


		public Query(Conversation conversation, long start, long end) {
			this(conversation.getAccount(), start, end);
			this.conversation = conversation;
		}

		public Query(Conversation conversation, long start, long end, boolean catchup) {
			this(conversation,start,end);
			this.pagingOrder = catchup ? PagingOrder.NORMAL : PagingOrder.REVERSE;
			this.catchup = catchup;
		}

		public Query(Account account, long start, long end) {
			this.account = account;
			this.start = start;
			this.end = end;
			this.queryId = new BigInteger(50, mXmppConnectionService.getRNG()).toString(32);
		}
		
		private Query page(String reference) {
			Query query = new Query(this.account,this.start,this.end);
			query.reference = reference;
			query.conversation = conversation;
			query.totalCount = totalCount;
			query.actualCount = actualCount;
			query.callback = callback;
			query.catchup = catchup;
			return query;
		}

		public boolean isLegacy() {
			if (conversation == null || conversation.getMode() == Conversation.MODE_SINGLE) {
				return account.getXmppConnection().getFeatures().mamLegacy();
			} else {
				return conversation.getMucOptions().mamLegacy();
			}
		}

		public Query next(String reference) {
			Query query = page(reference);
			query.pagingOrder = PagingOrder.NORMAL;
			return query;
		}

		public Query prev(String reference) {
			Query query = page(reference);
			query.pagingOrder = PagingOrder.REVERSE;
			return query;
		}

		public String getReference() {
			return reference;
		}

		public PagingOrder getPagingOrder() {
			return this.pagingOrder;
		}

		public String getQueryId() {
			return queryId;
		}

		public Jid getWith() {
			return conversation == null ? null : conversation.getJid().toBareJid();
		}

		public boolean muc() {
			return conversation != null && conversation.getMode() == Conversation.MODE_MULTI;
		}

		public long getStart() {
			return start;
		}

		public boolean isCatchup() {
			return catchup;
		}

		public void setCallback(XmppConnectionService.OnMoreMessagesLoaded callback) {
			this.callback = callback;
		}

		public void callback(boolean done) {
			if (this.callback != null) {
				this.callback.onMoreMessagesLoaded(actualCount,conversation);
				if (done) {
					this.callback.informUser(R.string.no_more_history_on_server);
				}
			}
		}

		public long getEnd() {
			return end;
		}

		public Conversation getConversation() {
			return conversation;
		}

		public Account getAccount() {
			return this.account;
		}

		public void incrementMessageCount() {
			this.totalCount++;
		}

		public void incrementActualMessageCount() {
			this.actualCount++;
		}

		public int getTotalCount() {
			return this.totalCount;
		}

		public int getActualMessageCount() {
			return this.actualCount;
		}

		public boolean validFrom(Jid from) {
			if (muc()) {
				return getWith().equals(from);
			} else {
				return (from == null) || account.getJid().toBareJid().equals(from.toBareJid());
			}
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			if (this.muc()) {
				builder.append("to=");
				builder.append(this.getWith().toString());
			} else {
				builder.append("with=");
				if (this.getWith() == null) {
					builder.append("*");
				} else {
					builder.append(getWith().toString());
				}
			}
			builder.append(", start=");
			builder.append(AbstractGenerator.getTimestamp(this.start));
			builder.append(", end=");
			builder.append(AbstractGenerator.getTimestamp(this.end));
			builder.append(", order="+pagingOrder.toString());
			if (this.reference!=null) {
				if (this.pagingOrder == PagingOrder.NORMAL) {
					builder.append(", after=");
				} else {
					builder.append(", before=");
				}
				builder.append(this.reference);
			}
			builder.append(", catchup="+Boolean.toString(catchup));
			return builder.toString();
		}

		public boolean hasCallback() {
			return this.callback != null;
		}
	}
}
