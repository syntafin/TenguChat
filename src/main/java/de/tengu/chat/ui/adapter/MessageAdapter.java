package de.tengu.chat.ui.adapter;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tengu.chat.Config;
import de.tengu.chat.R;
import de.tengu.chat.crypto.axolotl.FingerprintStatus;
import de.tengu.chat.entities.Account;
import de.tengu.chat.entities.Conversation;
import de.tengu.chat.entities.DownloadableFile;
import de.tengu.chat.entities.Message;
import de.tengu.chat.entities.Message.FileParams;
import de.tengu.chat.entities.Transferable;
import de.tengu.chat.persistance.FileBackend;
import de.tengu.chat.services.MessageArchiveService;
import de.tengu.chat.services.NotificationService;
import de.tengu.chat.ui.ConversationActivity;
import de.tengu.chat.ui.service.AudioPlayer;
import de.tengu.chat.ui.text.DividerSpan;
import de.tengu.chat.ui.text.QuoteSpan;
import de.tengu.chat.ui.widget.ClickableMovementMethod;
import de.tengu.chat.ui.widget.CopyTextView;
import de.tengu.chat.ui.widget.ListSelectionManager;
import de.tengu.chat.utils.CryptoHelper;
import de.tengu.chat.utils.Emoticons;
import de.tengu.chat.utils.GeoHelper;
import de.tengu.chat.utils.Patterns;
import de.tengu.chat.utils.UIHelper;
import de.tengu.chat.xmpp.mam.MamReference;

public class MessageAdapter extends ArrayAdapter<Message> implements CopyTextView.CopyHandler {

	private static final int SENT = 0;
	private static final int RECEIVED = 1;
	private static final int STATUS = 2;
	private static final int DATE_SEPARATOR = 3;

	public static final String DATE_SEPARATOR_BODY = "DATE_SEPARATOR";

	private static final Pattern XMPP_PATTERN = Pattern
			.compile("xmpp\\:(?:(?:["
					+ Patterns.GOOD_IRI_CHAR
					+ "\\;\\/\\?\\@\\&\\=\\#\\~\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])"
					+ "|(?:\\%[a-fA-F0-9]{2}))+");

	private static final Linkify.TransformFilter WEBURL_TRANSFORM_FILTER = new Linkify.TransformFilter() {
		@Override
		public String transformUrl(Matcher matcher, String url) {
			if (url == null) {
				return null;
			}
			final String lcUrl = url.toLowerCase(Locale.US);
			if (lcUrl.startsWith("http://") || lcUrl.startsWith("https://")) {
				return url;
			} else {
				return "http://"+url;
			}
		}
	};
	private static final Linkify.MatchFilter WEBURL_MATCH_FILTER = new Linkify.MatchFilter() {
		@Override
		public boolean acceptMatch(CharSequence cs, int start, int end) {
			return start < 1 || (cs.charAt(start-1) != '@' && cs.charAt(start-1) != '.' && !cs.subSequence(Math.max(0,start - 3),start).equals("://"));
		}
	};

	private final ConversationActivity activity;

	private DisplayMetrics metrics;

	private OnContactPictureClicked mOnContactPictureClickedListener;
	private OnContactPictureLongClicked mOnContactPictureLongClickedListener;

	private boolean mIndicateReceived = false;
	private boolean mUseGreenBackground = false;

	private OnQuoteListener onQuoteListener;

	private final ListSelectionManager listSelectionManager = new ListSelectionManager();

	private final AudioPlayer audioPlayer;

	public MessageAdapter(ConversationActivity activity, List<Message> messages) {
		super(activity, 0, messages);
		this.audioPlayer = new AudioPlayer(this);
		this.activity = activity;
		metrics = getContext().getResources().getDisplayMetrics();
		updatePreferences();
	}

	public void setOnContactPictureClicked(OnContactPictureClicked listener) {
		this.mOnContactPictureClickedListener = listener;
	}

	public void setOnContactPictureLongClicked(
			OnContactPictureLongClicked listener) {
		this.mOnContactPictureLongClickedListener = listener;
			}

	public void setOnQuoteListener(OnQuoteListener listener) {
		this.onQuoteListener = listener;
	}

	@Override
	public int getViewTypeCount() {
		return 4;
	}

	public int getItemViewType(Message message) {
		if (message.getType() == Message.TYPE_STATUS) {
			if (DATE_SEPARATOR_BODY.equals(message.getBody())) {
				return DATE_SEPARATOR;
			} else {
				return STATUS;
			}
		} else if (message.getStatus() <= Message.STATUS_RECEIVED) {
			return RECEIVED;
		}

		return SENT;
	}

	@Override
	public int getItemViewType(int position) {
		return this.getItemViewType(getItem(position));
	}

	public int getMessageTextColor(boolean onDark, boolean primary) {
		if (onDark) {
			return ContextCompat.getColor(activity, primary ? R.color.white : R.color.white70);
		} else {
			return ContextCompat.getColor(activity, primary ? R.color.black87 : R.color.black54);
		}
	}

	private void displayStatus(ViewHolder viewHolder, Message message, int type, boolean darkBackground) {
		String filesize = null;
		String info = null;
		boolean error = false;
		if (viewHolder.indicatorReceived != null) {
			viewHolder.indicatorReceived.setVisibility(View.GONE);
		}

		if (viewHolder.edit_indicator != null) {
			if (message.edited()) {
				viewHolder.edit_indicator.setVisibility(View.VISIBLE);
				viewHolder.edit_indicator.setImageResource(darkBackground ? R.drawable.ic_mode_edit_white_18dp : R.drawable.ic_mode_edit_black_18dp);
				viewHolder.edit_indicator.setAlpha(darkBackground ? 0.7f : 0.57f);
			} else {
				viewHolder.edit_indicator.setVisibility(View.GONE);
			}
		}
		boolean multiReceived = message.getConversation().getMode() == Conversation.MODE_MULTI
			&& message.getMergedStatus() <= Message.STATUS_RECEIVED;
		if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE || message.getTransferable() != null) {
			FileParams params = message.getFileParams();
			if (params.size > (1.5 * 1024 * 1024)) {
				filesize = params.size / (1024 * 1024)+ " MiB";
			} else if (params.size >= 1024) {
				filesize = params.size / 1024 + " KiB";
			} else if (params.size > 0){
				filesize = params.size + " B";
			}
			if (message.getTransferable() != null && message.getTransferable().getStatus() == Transferable.STATUS_FAILED) {
				error = true;
			}
		}
		switch (message.getMergedStatus()) {
			case Message.STATUS_WAITING:
				info = getContext().getString(R.string.waiting);
				break;
			case Message.STATUS_UNSEND:
				Transferable d = message.getTransferable();
				if (d!=null) {
					info = getContext().getString(R.string.sending_file,d.getProgress());
				} else {
					info = getContext().getString(R.string.sending);
				}
				break;
			case Message.STATUS_OFFERED:
				info = getContext().getString(R.string.offering);
				break;
			case Message.STATUS_SEND_RECEIVED:
				if (mIndicateReceived) {
					viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
				}
				break;
			case Message.STATUS_SEND_DISPLAYED:
				if (mIndicateReceived) {
					viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
				}
				break;
			case Message.STATUS_SEND_FAILED:
				info = getContext().getString(R.string.send_failed);
				error = true;
				break;
			default:
				if (multiReceived) {
					info = UIHelper.getMessageDisplayName(message);
				}
				break;
		}
		if (error && type == SENT) {
			viewHolder.time.setTextColor(activity.getWarningTextColor());
		} else {
			viewHolder.time.setTextColor(this.getMessageTextColor(darkBackground,false));
		}
		if (message.getEncryption() == Message.ENCRYPTION_NONE) {
			viewHolder.indicator.setVisibility(View.GONE);
		} else {
			boolean verified = false;
			if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
				final FingerprintStatus status = message.getConversation()
						.getAccount().getAxolotlService().getFingerprintTrust(
								message.getFingerprint());
				if (status != null && status.isVerified()) {
					verified = true;
				}
			}
			if (verified) {
				viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_verified_user_white_18dp : R.drawable.ic_verified_user_black_18dp);
			} else {
				viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_lock_white_18dp : R.drawable.ic_lock_black_18dp);
			}
			if (darkBackground) {
				viewHolder.indicator.setAlpha(0.7f);
			} else {
				viewHolder.indicator.setAlpha(0.57f);
			}
			viewHolder.indicator.setVisibility(View.VISIBLE);
		}

		String formatedTime = UIHelper.readableTimeDifferenceFull(getContext(),
				message.getMergedTimeSent());
		if (message.getStatus() <= Message.STATUS_RECEIVED) {
			if ((filesize != null) && (info != null)) {
				viewHolder.time.setText(formatedTime + " \u00B7 " + filesize +" \u00B7 " + info);
			} else if ((filesize == null) && (info != null)) {
				viewHolder.time.setText(formatedTime + " \u00B7 " + info);
			} else if ((filesize != null) && (info == null)) {
				viewHolder.time.setText(formatedTime + " \u00B7 " + filesize);
			} else {
				viewHolder.time.setText(formatedTime);
			}
		} else {
			if ((filesize != null) && (info != null)) {
				viewHolder.time.setText(filesize + " \u00B7 " + info);
			} else if ((filesize == null) && (info != null)) {
				if (error) {
					viewHolder.time.setText(info + " \u00B7 " + formatedTime);
				} else {
					viewHolder.time.setText(info);
				}
			} else if ((filesize != null) && (info == null)) {
				viewHolder.time.setText(filesize + " \u00B7 " + formatedTime);
			} else {
				viewHolder.time.setText(formatedTime);
			}
		}
	}

	private void displayInfoMessage(ViewHolder viewHolder, String text, boolean darkBackground) {
		viewHolder.download_button.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		viewHolder.messageBody.setText(text);
		viewHolder.messageBody.setTextColor(getMessageTextColor(darkBackground, false));
		viewHolder.messageBody.setTypeface(null, Typeface.ITALIC);
		viewHolder.messageBody.setTextIsSelectable(false);
	}

	private void displayDecryptionFailed(ViewHolder viewHolder, boolean darkBackground) {
		viewHolder.download_button.setVisibility(View.GONE);
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		viewHolder.messageBody.setText(getContext().getString(
				R.string.decryption_failed));
		viewHolder.messageBody.setTextColor(getMessageTextColor(darkBackground, false));
		viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
		viewHolder.messageBody.setTextIsSelectable(false);
	}

	private void displayEmojiMessage(final ViewHolder viewHolder, final String body) {
		viewHolder.download_button.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		Spannable span = new SpannableString(body);
		float size = Emoticons.isEmoji(body) ? 3.0f : 2.0f;
		span.setSpan(new RelativeSizeSpan(size), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		viewHolder.messageBody.setText(span);
	}

	private int applyQuoteSpan(SpannableStringBuilder body, int start, int end, boolean darkBackground) {
		if (start > 1 && !"\n\n".equals(body.subSequence(start - 2, start).toString())) {
			body.insert(start++, "\n");
			body.setSpan(new DividerSpan(false), start - 2, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			end++;
		}
		if (end < body.length() - 1 && !"\n\n".equals(body.subSequence(end, end + 2).toString())) {
			body.insert(end, "\n");
			body.setSpan(new DividerSpan(false), end, end + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		int color = darkBackground ? this.getMessageTextColor(darkBackground, false)
				: ContextCompat.getColor(activity, R.color.bubble);
		DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
		body.setSpan(new QuoteSpan(color, metrics), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		return 0;
	}

	/**
	 * Applies QuoteSpan to group of lines which starts with > or » characters.
	 * Appends likebreaks and applies DividerSpan to them to show a padding between quote and text.
	 */
	private boolean handleTextQuotes(SpannableStringBuilder body, boolean darkBackground) {
		boolean startsWithQuote = false;
		char previous = '\n';
		int lineStart = -1;
		int lineTextStart = -1;
		int quoteStart = -1;
		for (int i = 0; i <= body.length(); i++) {
			char current = body.length() > i ? body.charAt(i) : '\n';
			if (lineStart == -1) {
				if (previous == '\n') {
					if ((current == '>' && UIHelper.isPositionFollowedByQuoteableCharacter(body,i))
							|| current == '\u00bb' && !UIHelper.isPositionFollowedByQuote(body,i)) {
						// Line start with quote
						lineStart = i;
						if (quoteStart == -1) quoteStart = i;
						if (i == 0) startsWithQuote = true;
					} else if (quoteStart >= 0) {
						// Line start without quote, apply spans there
						applyQuoteSpan(body, quoteStart, i - 1, darkBackground);
						quoteStart = -1;
					}
				}
			} else {
				// Remove extra spaces between > and first character in the line
				// > character will be removed too
				if (current != ' ' && lineTextStart == -1) {
					lineTextStart = i;
				}
				if (current == '\n') {
					body.delete(lineStart, lineTextStart);
					i -= lineTextStart - lineStart;
					if (i == lineStart) {
						// Avoid empty lines because span over empty line can be hidden
						body.insert(i++, " ");
					}
					lineStart = -1;
					lineTextStart = -1;
				}
			}
			previous = current;
		}
		if (quoteStart >= 0) {
			// Apply spans to finishing open quote
			applyQuoteSpan(body, quoteStart, body.length(), darkBackground);
		}
		return startsWithQuote;
	}

	private void displayTextMessage(final ViewHolder viewHolder, final Message message, boolean darkBackground, int type) {
		viewHolder.download_button.setVisibility(View.GONE);
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		if (message.getBody() != null) {
			final String nick = UIHelper.getMessageDisplayName(message);
			SpannableStringBuilder body = message.getMergedBody();
			boolean hasMeCommand = message.hasMeCommand();
			if (hasMeCommand) {
				body = body.replace(0, Message.ME_COMMAND.length(), nick + " ");
			}
			if (body.length() > Config.MAX_DISPLAY_MESSAGE_CHARS) {
				body = new SpannableStringBuilder(body, 0, Config.MAX_DISPLAY_MESSAGE_CHARS);
				body.append("\u2026");
			}
			Message.MergeSeparator[] mergeSeparators = body.getSpans(0, body.length(), Message.MergeSeparator.class);
			for (Message.MergeSeparator mergeSeparator : mergeSeparators) {
				int start = body.getSpanStart(mergeSeparator);
				int end = body.getSpanEnd(mergeSeparator);
				body.setSpan(new DividerSpan(true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
			boolean startsWithQuote = handleTextQuotes(body, darkBackground);
			if (message.getType() != Message.TYPE_PRIVATE) {
				if (hasMeCommand) {
					body.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, nick.length(),
							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			} else {
				String privateMarker;
				if (message.getStatus() <= Message.STATUS_RECEIVED) {
					privateMarker = activity.getString(R.string.private_message);
				} else {
					final String to;
					if (message.getCounterpart() != null) {
						to = message.getCounterpart().getResourcepart();
					} else {
						to = "";
					}
					privateMarker = activity.getString(R.string.private_message_to, to);
				}
				body.insert(0, privateMarker);
				int privateMarkerIndex = privateMarker.length();
				if (startsWithQuote) {
					body.insert(privateMarkerIndex, "\n\n");
					body.setSpan(new DividerSpan(false), privateMarkerIndex, privateMarkerIndex + 2,
							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				} else {
					body.insert(privateMarkerIndex, " ");
				}
				body.setSpan(new ForegroundColorSpan(getMessageTextColor(darkBackground, false)),
						0, privateMarkerIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				body.setSpan(new StyleSpan(Typeface.BOLD),
						0, privateMarkerIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				if (hasMeCommand) {
					body.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), privateMarkerIndex + 1,
							privateMarkerIndex + 1 + nick.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
			if (message.getConversation().getMode() == Conversation.MODE_MULTI && message.getStatus() == Message.STATUS_RECEIVED) {
				Pattern pattern = NotificationService.generateNickHighlightPattern(message.getConversation().getMucOptions().getActualNick());
				Matcher matcher = pattern.matcher(body);
				while(matcher.find()) {
					body.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
			Linkify.addLinks(body, XMPP_PATTERN, "xmpp");
			Linkify.addLinks(body, Patterns.AUTOLINK_WEB_URL, "http", WEBURL_MATCH_FILTER, WEBURL_TRANSFORM_FILTER);
			Linkify.addLinks(body, GeoHelper.GEO_URI, "geo");
			viewHolder.messageBody.setAutoLinkMask(0);
			viewHolder.messageBody.setText(body);
			viewHolder.messageBody.setTextIsSelectable(true);
			viewHolder.messageBody.setMovementMethod(ClickableMovementMethod.getInstance());
			listSelectionManager.onUpdate(viewHolder.messageBody, message);
		} else {
			viewHolder.messageBody.setText("");
			viewHolder.messageBody.setTextIsSelectable(false);
		}
		viewHolder.messageBody.setTextColor(this.getMessageTextColor(darkBackground, true));
		viewHolder.messageBody.setLinkTextColor(this.getMessageTextColor(darkBackground, true));
		viewHolder.messageBody.setHighlightColor(ContextCompat.getColor(activity, darkBackground
				? (type == SENT || !mUseGreenBackground ? R.color.black26 : R.color.grey800) : R.color.grey500));
		viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
	}

	private void displayDownloadableMessage(ViewHolder viewHolder, final Message message, String text) {
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.download_button.setVisibility(View.VISIBLE);
		viewHolder.download_button.setText(text);
		viewHolder.download_button.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				activity.startDownloadable(message);
			}
		});
	}

	private void displayOpenableMessage(ViewHolder viewHolder,final Message message) {
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.download_button.setVisibility(View.VISIBLE);
		viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message)));
		viewHolder.download_button.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				openDownloadable(message);
			}
		});
	}

	private void displayLocationMessage(ViewHolder viewHolder, final Message message) {
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.download_button.setVisibility(View.VISIBLE);
		viewHolder.download_button.setText(R.string.show_location);
		viewHolder.download_button.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				showLocation(message);
			}
		});
	}

	private void displayAudioMessage(ViewHolder viewHolder, Message message, boolean darkBackground) {
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.download_button.setVisibility(View.GONE);
		final RelativeLayout audioPlayer = viewHolder.audioPlayer;
		audioPlayer.setVisibility(View.VISIBLE);
		AudioPlayer.ViewHolder.get(audioPlayer).setDarkBackground(darkBackground);
		this.audioPlayer.init(audioPlayer, message);
	}


	private void displayImageMessage(ViewHolder viewHolder, final Message message) {
		viewHolder.download_button.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.image.setVisibility(View.VISIBLE);
		FileParams params = message.getFileParams();
		double target = metrics.density * 288;
		int scaledW;
		int scaledH;
		if (Math.max(params.height, params.width) * metrics.density <= target) {
			scaledW = (int) (params.width * metrics.density);
			scaledH = (int) (params.height * metrics.density);
		} else if (Math.max(params.height,params.width) <= target) {
			scaledW = params.width;
			scaledH = params.height;
		} else if (params.width <= params.height) {
			scaledW = (int) (params.width / ((double) params.height / target));
			scaledH = (int) target;
		} else {
			scaledW = (int) target;
			scaledH = (int) (params.height / ((double) params.width / target));
		}
		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(scaledW, scaledH);
		layoutParams.setMargins(0, (int) (metrics.density * 4), 0, (int) (metrics.density * 4));
		viewHolder.image.setLayoutParams(layoutParams);
		activity.loadBitmap(message, viewHolder.image);
		viewHolder.image.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				openDownloadable(message);
			}
		});
	}

	private void loadMoreMessages(Conversation conversation) {
		conversation.setLastClearHistory(0,null);
		activity.xmppConnectionService.updateConversation(conversation);
		conversation.setHasMessagesLeftOnServer(true);
		conversation.setFirstMamReference(null);
		long timestamp = conversation.getLastMessageTransmitted().getTimestamp();
		if (timestamp == 0) {
			timestamp = System.currentTimeMillis();
		}
		conversation.messagesLoaded.set(true);
		MessageArchiveService.Query query = activity.xmppConnectionService.getMessageArchiveService().query(conversation, new MamReference(0), timestamp, false);
		if (query != null) {
			Toast.makeText(activity, R.string.fetching_history_from_server, Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(activity,R.string.not_fetching_history_retention_period, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		final Message message = getItem(position);
		final boolean omemoEncryption = message.getEncryption() == Message.ENCRYPTION_AXOLOTL;
		final boolean isInValidSession = message.isValidInSession() && (!omemoEncryption || message.isTrusted());
		final Conversation conversation = message.getConversation();
		final Account account = conversation.getAccount();
		final int type = getItemViewType(position);
		ViewHolder viewHolder;
		if (view == null) {
			viewHolder = new ViewHolder();
			switch (type) {
				case DATE_SEPARATOR:
					view = activity.getLayoutInflater().inflate(R.layout.message_date_bubble, parent, false);
					viewHolder.status_message = (TextView) view.findViewById(R.id.message_body);
					viewHolder.message_box = (LinearLayout) view.findViewById(R.id.message_box);
					break;
				case SENT:
					view = activity.getLayoutInflater().inflate(
							R.layout.message_sent, parent, false);
					viewHolder.message_box = (LinearLayout) view
						.findViewById(R.id.message_box);
					viewHolder.contact_picture = (ImageView) view
						.findViewById(R.id.message_photo);
					viewHolder.download_button = (Button) view
						.findViewById(R.id.download_button);
					viewHolder.indicator = (ImageView) view
						.findViewById(R.id.security_indicator);
					viewHolder.edit_indicator = (ImageView) view.findViewById(R.id.edit_indicator);
					viewHolder.image = (ImageView) view
						.findViewById(R.id.message_image);
					viewHolder.messageBody = (CopyTextView) view
						.findViewById(R.id.message_body);
					viewHolder.time = (TextView) view
						.findViewById(R.id.message_time);
					viewHolder.indicatorReceived = (ImageView) view
						.findViewById(R.id.indicator_received);
					viewHolder.audioPlayer = (RelativeLayout) view.findViewById(R.id.audio_player);
					break;
				case RECEIVED:
					view = activity.getLayoutInflater().inflate(
							R.layout.message_received, parent, false);
					viewHolder.message_box = (LinearLayout) view
						.findViewById(R.id.message_box);
					viewHolder.contact_picture = (ImageView) view
						.findViewById(R.id.message_photo);
					viewHolder.download_button = (Button) view
						.findViewById(R.id.download_button);
					viewHolder.indicator = (ImageView) view
						.findViewById(R.id.security_indicator);
					viewHolder.edit_indicator = (ImageView) view.findViewById(R.id.edit_indicator);
					viewHolder.image = (ImageView) view
						.findViewById(R.id.message_image);
					viewHolder.messageBody = (CopyTextView) view
						.findViewById(R.id.message_body);
					viewHolder.time = (TextView) view
						.findViewById(R.id.message_time);
					viewHolder.indicatorReceived = (ImageView) view
						.findViewById(R.id.indicator_received);
					viewHolder.encryption = (TextView) view.findViewById(R.id.message_encryption);
					viewHolder.audioPlayer = (RelativeLayout) view.findViewById(R.id.audio_player);
					break;
				case STATUS:
					view = activity.getLayoutInflater().inflate(R.layout.message_status, parent, false);
					viewHolder.contact_picture = (ImageView) view.findViewById(R.id.message_photo);
					viewHolder.status_message = (TextView) view.findViewById(R.id.status_message);
					viewHolder.load_more_messages = (Button) view.findViewById(R.id.load_more_messages);
					break;
				default:
					throw new AssertionError("Unknown view type");
			}
			if (viewHolder.messageBody != null) {
				listSelectionManager.onCreate(viewHolder.messageBody,
						new MessageBodyActionModeCallback(viewHolder.messageBody));
				viewHolder.messageBody.setCopyHandler(this);
			}
			view.setTag(viewHolder);
		} else {
			viewHolder = (ViewHolder) view.getTag();
			if (viewHolder == null) {
				return view;
			}
		}

		boolean darkBackground = type == RECEIVED && (!isInValidSession || mUseGreenBackground) || activity.isDarkTheme();

		if (type == DATE_SEPARATOR) {
			if (UIHelper.today(message.getTimeSent())) {
				viewHolder.status_message.setText(R.string.today);
			} else if (UIHelper.yesterday(message.getTimeSent())) {
				viewHolder.status_message.setText(R.string.yesterday);
			} else {
				viewHolder.status_message.setText(DateUtils.formatDateTime(activity,message.getTimeSent(),DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));
			}
			viewHolder.message_box.setBackgroundResource(activity.isDarkTheme() ? R.drawable.date_bubble_grey : R.drawable.date_bubble_white);
			viewHolder.status_message.setTextColor(activity.getSecondaryTextColor());
			return view;
		} else if (type == STATUS) {
			if ("LOAD_MORE".equals(message.getBody())) {
				viewHolder.status_message.setVisibility(View.GONE);
				viewHolder.contact_picture.setVisibility(View.GONE);
				viewHolder.load_more_messages.setVisibility(View.VISIBLE);
				viewHolder.load_more_messages.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						loadMoreMessages(message.getConversation());
					}
				});
			} else {
				viewHolder.status_message.setVisibility(View.VISIBLE);
				viewHolder.load_more_messages.setVisibility(View.GONE);
				viewHolder.status_message.setText(message.getBody());
				boolean showAvatar;
				if (conversation.getMode() == Conversation.MODE_SINGLE) {
					showAvatar = true;
					loadAvatar(message,viewHolder.contact_picture,activity.getPixel(32));
				} else if (message.getCounterpart() != null ){
					showAvatar = true;
					loadAvatar(message,viewHolder.contact_picture,activity.getPixel(32));
				} else {
					showAvatar = false;
				}
				if (showAvatar) {
					viewHolder.contact_picture.setAlpha(0.5f);
					viewHolder.contact_picture.setVisibility(View.VISIBLE);
				} else {
					viewHolder.contact_picture.setVisibility(View.GONE);
				}
			}
			return view;
		} else {
			loadAvatar(message, viewHolder.contact_picture,activity.getPixel(48));
		}

		viewHolder.contact_picture
			.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					if (MessageAdapter.this.mOnContactPictureClickedListener != null) {
						MessageAdapter.this.mOnContactPictureClickedListener
								.onContactPictureClicked(message);
					}

				}
			});
		viewHolder.contact_picture
			.setOnLongClickListener(new OnLongClickListener() {

				@Override
				public boolean onLongClick(View v) {
					if (MessageAdapter.this.mOnContactPictureLongClickedListener != null) {
						MessageAdapter.this.mOnContactPictureLongClickedListener
								.onContactPictureLongClicked(message);
						return true;
					} else {
						return false;
					}
				}
			});

		final Transferable transferable = message.getTransferable();
		if (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING) {
			if (transferable.getStatus() == Transferable.STATUS_OFFER) {
				displayDownloadableMessage(viewHolder,message,activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, message)));
			} else if (transferable.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE) {
				displayDownloadableMessage(viewHolder, message, activity.getString(R.string.check_x_filesize, UIHelper.getFileDescriptionString(activity, message)));
			} else {
				displayInfoMessage(viewHolder, UIHelper.getMessagePreview(activity, message).first,darkBackground);
			}
		} else if (message.getType() == Message.TYPE_IMAGE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
			displayImageMessage(viewHolder, message);
		} else if (message.getType() == Message.TYPE_FILE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
			if (message.getFileParams().width > 0 && message.getFileParams().height > 0) {
				displayImageMessage(viewHolder, message);
			} else if (message.getFileParams().runtime > 0) {
				displayAudioMessage(viewHolder, message, darkBackground);
			} else {
				displayOpenableMessage(viewHolder, message);
			}
		} else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
			if (account.isPgpDecryptionServiceConnected()) {
				if (!account.hasPendingPgpIntent(conversation)) {
					displayInfoMessage(viewHolder, activity.getString(R.string.message_decrypting), darkBackground);
				} else {
					displayInfoMessage(viewHolder, activity.getString(R.string.pgp_message), darkBackground);
				}
			} else {
				displayInfoMessage(viewHolder,activity.getString(R.string.install_openkeychain),darkBackground);
				viewHolder.message_box.setOnClickListener(new OnClickListener() {

							@Override
							public void onClick(View v) {
								activity.showInstallPgpDialog();
							}
						});
			}
		} else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
			displayDecryptionFailed(viewHolder,darkBackground);
		} else {
			if (message.isGeoUri()) {
				displayLocationMessage(viewHolder,message);
			} else if (message.bodyIsOnlyEmojis()) {
				displayEmojiMessage(viewHolder, message.getBody().trim());
			} else if (message.treatAsDownloadable()) {
				try {
					URL url = new URL(message.getBody());
					displayDownloadableMessage(viewHolder,
							message,
							activity.getString(R.string.check_x_filesize_on_host,
									UIHelper.getFileDescriptionString(activity, message),
									url.getHost()));
				} catch (Exception e) {
					displayDownloadableMessage(viewHolder,
							message,
							activity.getString(R.string.check_x_filesize,
									UIHelper.getFileDescriptionString(activity, message)));
				}
			} else {
				displayTextMessage(viewHolder, message, darkBackground, type);
			}
		}

		if (type == RECEIVED) {
			if(isInValidSession) {
				int bubble;
				if (!mUseGreenBackground) {
					bubble = activity.getThemeResource(R.attr.message_bubble_received_monochrome, R.drawable.message_bubble_received_white);
				} else {
					bubble = activity.getThemeResource(R.attr.message_bubble_received_green, R.drawable.message_bubble_received);
				}
				viewHolder.message_box.setBackgroundResource(bubble);
				viewHolder.encryption.setVisibility(View.GONE);
			} else {
				viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_warning);
				viewHolder.encryption.setVisibility(View.VISIBLE);
				if (omemoEncryption && !message.isTrusted()) {
					viewHolder.encryption.setText(R.string.not_trusted);
				} else {
					viewHolder.encryption.setText(CryptoHelper.encryptionTypeToText(message.getEncryption()));
				}
			}
		}

		displayStatus(viewHolder, message, type, darkBackground);

		return view;
	}

	@Override
	public void notifyDataSetChanged() {
		listSelectionManager.onBeforeNotifyDataSetChanged();
		super.notifyDataSetChanged();
		listSelectionManager.onAfterNotifyDataSetChanged();
	}

	private String transformText(CharSequence text, int start, int end, boolean forCopy) {
		SpannableStringBuilder builder = new SpannableStringBuilder(text);
		Object copySpan = new Object();
		builder.setSpan(copySpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		DividerSpan[] dividerSpans = builder.getSpans(0, builder.length(), DividerSpan.class);
		for (DividerSpan dividerSpan : dividerSpans) {
			builder.replace(builder.getSpanStart(dividerSpan), builder.getSpanEnd(dividerSpan),
					dividerSpan.isLarge() ? "\n\n" : "\n");
		}
		start = builder.getSpanStart(copySpan);
		end = builder.getSpanEnd(copySpan);
		if (start == -1 || end == -1) return "";
		builder = new SpannableStringBuilder(builder, start, end);
		if (forCopy) {
			QuoteSpan[] quoteSpans = builder.getSpans(0, builder.length(), QuoteSpan.class);
			for (QuoteSpan quoteSpan : quoteSpans) {
				builder.insert(builder.getSpanStart(quoteSpan), "> ");
			}
		}
		return builder.toString();
	}

	@Override
	public String transformTextForCopy(CharSequence text, int start, int end) {
		if (text instanceof Spanned) {
			return transformText(text, start, end, true);
		} else {
			return text.toString().substring(start, end);
		}
	}

	public FileBackend getFileBackend() {
		return activity.xmppConnectionService.getFileBackend();
	}

	public void stopAudioPlayer() {
		audioPlayer.stop();
	}

	public interface OnQuoteListener {
		public void onQuote(String text);
	}

	private class MessageBodyActionModeCallback implements ActionMode.Callback {

		private final TextView textView;

		public MessageBodyActionModeCallback(TextView textView) {
			this.textView = textView;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			if (onQuoteListener != null) {
				int quoteResId = activity.getThemeResource(R.attr.icon_quote, R.drawable.ic_action_reply);
				// 3rd item is placed after "copy" item
				menu.add(0, android.R.id.button1, 3, R.string.quote).setIcon(quoteResId)
						.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
			}
			return false;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			if (item.getItemId() == android.R.id.button1) {
				int start = textView.getSelectionStart();
				int end = textView.getSelectionEnd();
				if (end > start) {
					String text = transformText(textView.getText(), start, end, false);
					if (onQuoteListener != null) {
						onQuoteListener.onQuote(text);
					}
					mode.finish();
				}
				return true;
			}
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {}
	}

	public void openDownloadable(Message message) {
		DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
		if (!file.exists()) {
			Toast.makeText(activity,R.string.file_deleted,Toast.LENGTH_SHORT).show();
			return;
		}
		Intent openIntent = new Intent(Intent.ACTION_VIEW);
		String mime = file.getMimeType();
		if (mime == null) {
			mime = "*/*";
		}
		Uri uri;
		try {
			uri = FileBackend.getUriForFile(activity, file);
		} catch (SecurityException e) {
			Toast.makeText(activity, activity.getString(R.string.no_permission_to_access_x, file.getAbsolutePath()), Toast.LENGTH_SHORT).show();
			return;
		}
		openIntent.setDataAndType(uri, mime);
		openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		PackageManager manager = activity.getPackageManager();
		List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
		if (info.size() == 0) {
			openIntent.setDataAndType(uri,"*/*");
		}
		try {
			getContext().startActivity(openIntent);
		}  catch (ActivityNotFoundException e) {
			Toast.makeText(activity,R.string.no_application_found_to_open_file,Toast.LENGTH_SHORT).show();
		}
	}

	public void showLocation(Message message) {
		for(Intent intent : GeoHelper.createGeoIntentsFromMessage(message)) {
			if (intent.resolveActivity(getContext().getPackageManager()) != null) {
				getContext().startActivity(intent);
				return;
			}
		}
		Toast.makeText(activity,R.string.no_application_found_to_display_location,Toast.LENGTH_SHORT).show();
	}

	public void updatePreferences() {
		this.mIndicateReceived = activity.indicateReceived();
		this.mUseGreenBackground = activity.useGreenBackground();
	}

	public TextView getMessageBody(View view) {
		final Object tag = view.getTag();
		if (tag instanceof ViewHolder) {
			final ViewHolder viewHolder = (ViewHolder) tag;
			return viewHolder.messageBody;
		}
		return null;
	}

	public interface OnContactPictureClicked {
		void onContactPictureClicked(Message message);
	}

	public interface OnContactPictureLongClicked {
		void onContactPictureLongClicked(Message message);
	}

	private static class ViewHolder {

		protected LinearLayout message_box;
		protected Button download_button;
		protected ImageView image;
		protected ImageView indicator;
		protected ImageView indicatorReceived;
		protected TextView time;
		protected CopyTextView messageBody;
		protected ImageView contact_picture;
		protected TextView status_message;
		protected TextView encryption;
		public Button load_more_messages;
		public ImageView edit_indicator;
		public RelativeLayout audioPlayer;
	}

	class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private Message message = null;
		private final int size;

		public BitmapWorkerTask(ImageView imageView, int size) {
			imageViewReference = new WeakReference<>(imageView);
			this.size = size;
		}

		@Override
		protected Bitmap doInBackground(Message... params) {
			return activity.avatarService().get(params[0], size, isCancelled());
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (bitmap != null && !isCancelled()) {
				final ImageView imageView = imageViewReference.get();
				if (imageView != null) {
					imageView.setImageBitmap(bitmap);
					imageView.setBackgroundColor(0x00000000);
				}
			}
		}
	}

	public void loadAvatar(Message message, ImageView imageView, int size) {
		if (cancelPotentialWork(message, imageView)) {
			final Bitmap bm = activity.avatarService().get(message, size, true);
			if (bm != null) {
				cancelPotentialWork(message, imageView);
				imageView.setImageBitmap(bm);
				imageView.setBackgroundColor(0x00000000);
			} else {
				imageView.setBackgroundColor(UIHelper.getColorForName(UIHelper.getMessageDisplayName(message)));
				imageView.setImageDrawable(null);
				final BitmapWorkerTask task = new BitmapWorkerTask(imageView, size);
				final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
				imageView.setImageDrawable(asyncDrawable);
				try {
					task.execute(message);
				} catch (final RejectedExecutionException ignored) {
				}
			}
		}
	}

	public static boolean cancelPotentialWork(Message message, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final Message oldMessage = bitmapWorkerTask.message;
			if (oldMessage == null || message != oldMessage) {
				bitmapWorkerTask.cancel(true);
			} else {
				return false;
			}
		}
		return true;
	}

	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

		public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
			super(res, bitmap);
			bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
		}

		public BitmapWorkerTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}
}
