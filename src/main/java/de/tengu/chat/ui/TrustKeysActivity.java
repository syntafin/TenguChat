package de.tengu.chat.ui;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;

import org.whispersystems.libsignal.IdentityKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.tengu.chat.Config;
import de.tengu.chat.R;
import de.tengu.chat.crypto.axolotl.AxolotlService;
import de.tengu.chat.crypto.axolotl.FingerprintStatus;
import de.tengu.chat.entities.Account;
import de.tengu.chat.entities.Conversation;
import de.tengu.chat.utils.CryptoHelper;
import de.tengu.chat.utils.XmppUri;
import de.tengu.chat.xmpp.OnKeyStatusUpdated;
import de.tengu.chat.xmpp.jid.InvalidJidException;
import de.tengu.chat.xmpp.jid.Jid;

public class TrustKeysActivity extends OmemoActivity implements OnKeyStatusUpdated {
	private List<Jid> contactJids;

	private Account mAccount;
	private Conversation mConversation;
	private TextView keyErrorMessage;
	private LinearLayout keyErrorMessageCard;
	private TextView ownKeysTitle;
	private LinearLayout ownKeys;
	private LinearLayout ownKeysCard;
	private LinearLayout foreignKeys;
	private Button mSaveButton;
	private Button mCancelButton;

	private AtomicBoolean mUseCameraHintShown = new AtomicBoolean(false);

	private AxolotlService.FetchStatus lastFetchReport = AxolotlService.FetchStatus.SUCCESS;

	private final Map<String, Boolean> ownKeysToTrust = new HashMap<>();
	private final Map<Jid,Map<String, Boolean>> foreignKeysToTrust = new HashMap<>();

	private final OnClickListener mSaveButtonListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			commitTrusts();
			finishOk();
		}
	};

	private final OnClickListener mCancelButtonListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			setResult(RESULT_CANCELED);
			finish();
		}
	};
	private Toast mUseCameraHintToast = null;

	@Override
	protected void refreshUiReal() {
		invalidateOptionsMenu();
		populateView();
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_trust_keys);
		this.contactJids = new ArrayList<>();
		for(String jid : getIntent().getStringArrayExtra("contacts")) {
			try {
				this.contactJids.add(Jid.fromString(jid));
			} catch (InvalidJidException e) {
				e.printStackTrace();
			}
		}

		keyErrorMessageCard = (LinearLayout) findViewById(R.id.key_error_message_card);
		keyErrorMessage = (TextView) findViewById(R.id.key_error_message);
		ownKeysTitle = (TextView) findViewById(R.id.own_keys_title);
		ownKeys = (LinearLayout) findViewById(R.id.own_keys_details);
		ownKeysCard = (LinearLayout) findViewById(R.id.own_keys_card);
		foreignKeys = (LinearLayout) findViewById(R.id.foreign_keys);
		mCancelButton = (Button) findViewById(R.id.cancel_button);
		mCancelButton.setOnClickListener(mCancelButtonListener);
		mSaveButton = (Button) findViewById(R.id.save_button);
		mSaveButton.setOnClickListener(mSaveButtonListener);


		if (getActionBar() != null) {
			getActionBar().setHomeButtonEnabled(true);
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}

		if (savedInstanceState != null) {
			mUseCameraHintShown.set(savedInstanceState.getBoolean("camera_hint_shown",false));
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putBoolean("camera_hint_shown", mUseCameraHintShown.get());
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.trust_keys, menu);
		MenuItem scanQrCode = menu.findItem(R.id.action_scan_qr_code);
		scanQrCode.setVisible(ownKeysToTrust.size() > 0 || foreignActuallyHasKeys());
		return super.onCreateOptionsMenu(menu);
	}

	private void showCameraToast() {
		mUseCameraHintToast = Toast.makeText(this,R.string.use_camera_icon_to_scan_barcode,Toast.LENGTH_LONG);
		ActionBar actionBar = getActionBar();
		mUseCameraHintToast.setGravity(Gravity.TOP | Gravity.END, 0 ,actionBar == null ? 0 : actionBar.getHeight());
		mUseCameraHintToast.show();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_scan_qr_code:
				if (hasPendingKeyFetches()) {
					Toast.makeText(this, R.string.please_wait_for_keys_to_be_fetched, Toast.LENGTH_SHORT).show();
				} else {
					new IntentIntegrator(this).initiateScan(Arrays.asList("AZTEC","QR_CODE"));
					return true;
				}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (mUseCameraHintToast != null) {
			mUseCameraHintToast.cancel();
		}
	}

	@Override
	protected void processFingerprintVerification(XmppUri uri) {
		if (mConversation != null
				&& mAccount != null
				&& uri.hasFingerprints()
				&& mAccount.getAxolotlService().getCryptoTargets(mConversation).contains(uri.getJid())) {
			boolean performedVerification = xmppConnectionService.verifyFingerprints(mAccount.getRoster().getContact(uri.getJid()),uri.getFingerprints());
			boolean keys = reloadFingerprints();
			if (performedVerification && !keys && !hasNoOtherTrustedKeys() && !hasPendingKeyFetches()) {
				Toast.makeText(this,R.string.all_omemo_keys_have_been_verified, Toast.LENGTH_SHORT).show();
				finishOk();
				return;
			} else if (performedVerification) {
				Toast.makeText(this,R.string.verified_fingerprints,Toast.LENGTH_SHORT).show();
			}
		} else {
			reloadFingerprints();
			Log.d(Config.LOGTAG,"xmpp uri was: "+uri.getJid()+" has Fingerprints: "+Boolean.toString(uri.hasFingerprints()));
			Toast.makeText(this,R.string.barcode_does_not_contain_fingerprints_for_this_conversation,Toast.LENGTH_SHORT).show();
		}
		populateView();
	}

	private void populateView() {
		setTitle(getString(R.string.trust_omemo_fingerprints));
		ownKeys.removeAllViews();
		foreignKeys.removeAllViews();
		boolean hasOwnKeys = false;
		boolean hasForeignKeys = false;
		for(final String fingerprint : ownKeysToTrust.keySet()) {
			hasOwnKeys = true;
			addFingerprintRowWithListeners(ownKeys, mAccount, fingerprint, false,
					FingerprintStatus.createActive(ownKeysToTrust.get(fingerprint)), false, false,
					new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
							ownKeysToTrust.put(fingerprint, isChecked);
							// own fingerprints have no impact on locked status.
						}
					}
			);
		}

		synchronized (this.foreignKeysToTrust) {
			for (Map.Entry<Jid, Map<String, Boolean>> entry : foreignKeysToTrust.entrySet()) {
				hasForeignKeys = true;
				final LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.keys_card, foreignKeys, false);
				final Jid jid = entry.getKey();
				final TextView header = (TextView) layout.findViewById(R.id.foreign_keys_title);
				final LinearLayout keysContainer = (LinearLayout) layout.findViewById(R.id.foreign_keys_details);
				final TextView informNoKeys = (TextView) layout.findViewById(R.id.no_keys_to_accept);
				header.setText(jid.toString());
				header.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						switchToContactDetails(mAccount.getRoster().getContact(jid));
					}
				});
				final Map<String, Boolean> fingerprints = entry.getValue();
				for (final String fingerprint : fingerprints.keySet()) {
					addFingerprintRowWithListeners(keysContainer, mAccount, fingerprint, false,
							FingerprintStatus.createActive(fingerprints.get(fingerprint)), false, false,
							new CompoundButton.OnCheckedChangeListener() {
								@Override
								public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
									fingerprints.put(fingerprint, isChecked);
									lockOrUnlockAsNeeded();
								}
							}
					);
				}
				if (fingerprints.size() == 0) {
					informNoKeys.setVisibility(View.VISIBLE);
					if (hasNoOtherTrustedKeys(jid)) {
						if (!mAccount.getRoster().getContact(jid).mutualPresenceSubscription()) {
							informNoKeys.setText(R.string.error_no_keys_to_trust_presence);
						} else {
							informNoKeys.setText(R.string.error_no_keys_to_trust_server_error);
						}
					} else {
						informNoKeys.setText(getString(R.string.no_keys_just_confirm, mAccount.getRoster().getContact(jid).getDisplayName()));
					}
				} else {
					informNoKeys.setVisibility(View.GONE);
				}
				foreignKeys.addView(layout);
			}
		}

		if ((hasOwnKeys || foreignActuallyHasKeys()) && mUseCameraHintShown.compareAndSet(false,true)) {
			showCameraToast();
		}

		ownKeysTitle.setText(mAccount.getJid().toBareJid().toString());
		ownKeysCard.setVisibility(hasOwnKeys ? View.VISIBLE : View.GONE);
		foreignKeys.setVisibility(hasForeignKeys ? View.VISIBLE : View.GONE);
		if(hasPendingKeyFetches()) {
			setFetching();
			lock();
		} else {
			if (!hasForeignKeys && hasNoOtherTrustedKeys()) {
				keyErrorMessageCard.setVisibility(View.VISIBLE);
				if (lastFetchReport == AxolotlService.FetchStatus.ERROR
						|| mAccount.getAxolotlService().fetchMapHasErrors(contactJids)) {
					if (anyWithoutMutualPresenceSubscription(contactJids)) {
						keyErrorMessage.setText(R.string.error_no_keys_to_trust_presence);
					} else {
						keyErrorMessage.setText(R.string.error_no_keys_to_trust_server_error);
					}
				} else {
					keyErrorMessage.setText(R.string.error_no_keys_to_trust);
				}
				ownKeys.removeAllViews();
				ownKeysCard.setVisibility(View.GONE);
				foreignKeys.removeAllViews();
				foreignKeys.setVisibility(View.GONE);
			}
			lockOrUnlockAsNeeded();
			setDone();
		}
	}

	private boolean anyWithoutMutualPresenceSubscription(List<Jid> contactJids){
		for(Jid jid : contactJids) {
			if (!mAccount.getRoster().getContact(jid).mutualPresenceSubscription()) {
				return true;
			}
		}
		return false;
	}

	private boolean foreignActuallyHasKeys() {
		synchronized (this.foreignKeysToTrust) {
			for (Map.Entry<Jid, Map<String, Boolean>> entry : foreignKeysToTrust.entrySet()) {
				if (entry.getValue().size() > 0) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean reloadFingerprints() {
		List<Jid> acceptedTargets = mConversation == null ? new ArrayList<Jid>() : mConversation.getAcceptedCryptoTargets();
		ownKeysToTrust.clear();
		AxolotlService service = this.mAccount.getAxolotlService();
		Set<IdentityKey> ownKeysSet = service.getKeysWithTrust(FingerprintStatus.createActiveUndecided());
		for(final IdentityKey identityKey : ownKeysSet) {
			final String fingerprint = CryptoHelper.bytesToHex(identityKey.getPublicKey().serialize());
			if(!ownKeysToTrust.containsKey(fingerprint)) {
				ownKeysToTrust.put(fingerprint, false);
			}
		}
		synchronized (this.foreignKeysToTrust) {
			foreignKeysToTrust.clear();
			for (Jid jid : contactJids) {
				Set<IdentityKey> foreignKeysSet = service.getKeysWithTrust(FingerprintStatus.createActiveUndecided(), jid);
				if (hasNoOtherTrustedKeys(jid) && ownKeysSet.size() == 0) {
					foreignKeysSet.addAll(service.getKeysWithTrust(FingerprintStatus.createActive(false), jid));
				}
				Map<String, Boolean> foreignFingerprints = new HashMap<>();
				for (final IdentityKey identityKey : foreignKeysSet) {
					final String fingerprint = CryptoHelper.bytesToHex(identityKey.getPublicKey().serialize());
					if (!foreignFingerprints.containsKey(fingerprint)) {
						foreignFingerprints.put(fingerprint, false);
					}
				}
				if (foreignFingerprints.size() > 0 || !acceptedTargets.contains(jid)) {
					foreignKeysToTrust.put(jid, foreignFingerprints);
				}
			}
		}
		return ownKeysSet.size() + foreignKeysToTrust.size() > 0;
	}

	public void onBackendConnected() {
		Intent intent = getIntent();
		this.mAccount = extractAccount(intent);
		if (this.mAccount != null && intent != null) {
			String uuid = intent.getStringExtra("conversation");
			this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
			if (this.mPendingFingerprintVerificationUri != null) {
				processFingerprintVerification(this.mPendingFingerprintVerificationUri);
				this.mPendingFingerprintVerificationUri = null;
			} else {
				reloadFingerprints();
				populateView();
				invalidateOptionsMenu();
			}
		}
	}

	private boolean hasNoOtherTrustedKeys() {
		return mAccount == null || mAccount.getAxolotlService().anyTargetHasNoTrustedKeys(contactJids);
	}

	private boolean hasNoOtherTrustedKeys(Jid contact) {
		return mAccount == null || mAccount.getAxolotlService().getNumTrustedKeys(contact) == 0;
	}

	private boolean hasPendingKeyFetches() {
		return mAccount != null && mAccount.getAxolotlService().hasPendingKeyFetches(mAccount, contactJids);
	}


	@Override
	public void onKeyStatusUpdated(final AxolotlService.FetchStatus report) {
		final boolean keysToTrust = reloadFingerprints();
		if (report != null) {
			lastFetchReport = report;
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (mUseCameraHintToast != null && !keysToTrust) {
						mUseCameraHintToast.cancel();
					}
					switch (report) {
						case ERROR:
							Toast.makeText(TrustKeysActivity.this,R.string.error_fetching_omemo_key,Toast.LENGTH_SHORT).show();
							break;
						case SUCCESS_TRUSTED:
							Toast.makeText(TrustKeysActivity.this,R.string.blindly_trusted_omemo_keys,Toast.LENGTH_LONG).show();
							break;
						case SUCCESS_VERIFIED:
							Toast.makeText(TrustKeysActivity.this,
									Config.X509_VERIFICATION ? R.string.verified_omemo_key_with_certificate : R.string.all_omemo_keys_have_been_verified,
									Toast.LENGTH_LONG).show();
							break;
					}
				}
			});

		}
		if (keysToTrust || hasPendingKeyFetches() || hasNoOtherTrustedKeys()) {
			refreshUi();
		} else {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					finishOk();
				}
			});

		}
	}

	private void finishOk() {
		Intent data = new Intent();
		data.putExtra("choice", getIntent().getIntExtra("choice", ConversationActivity.ATTACHMENT_CHOICE_INVALID));
		setResult(RESULT_OK, data);
		finish();
	}

	private void commitTrusts() {
		for(final String fingerprint :ownKeysToTrust.keySet()) {
			mAccount.getAxolotlService().setFingerprintTrust(
					fingerprint,
					FingerprintStatus.createActive(ownKeysToTrust.get(fingerprint)));
		}
		List<Jid> acceptedTargets = mConversation == null ? new ArrayList<Jid>() : mConversation.getAcceptedCryptoTargets();
		synchronized (this.foreignKeysToTrust) {
			for (Map.Entry<Jid, Map<String, Boolean>> entry : foreignKeysToTrust.entrySet()) {
				Jid jid = entry.getKey();
				Map<String, Boolean> value = entry.getValue();
				if (!acceptedTargets.contains(jid)) {
					acceptedTargets.add(jid);
				}
				for (final String fingerprint : value.keySet()) {
					mAccount.getAxolotlService().setFingerprintTrust(
							fingerprint,
							FingerprintStatus.createActive(value.get(fingerprint)));
				}
			}
		}
		if (mConversation != null && mConversation.getMode() == Conversation.MODE_MULTI) {
			mConversation.setAcceptedCryptoTargets(acceptedTargets);
			xmppConnectionService.updateConversation(mConversation);
		}
	}

	private void unlock() {
		mSaveButton.setEnabled(true);
		mSaveButton.setTextColor(getPrimaryTextColor());
	}

	private void lock() {
		mSaveButton.setEnabled(false);
		mSaveButton.setTextColor(getSecondaryTextColor());
	}

	private void lockOrUnlockAsNeeded() {
		synchronized (this.foreignKeysToTrust) {
			for (Jid jid : contactJids) {
				Map<String, Boolean> fingerprints = foreignKeysToTrust.get(jid);
				if (hasNoOtherTrustedKeys(jid) && (fingerprints == null || !fingerprints.values().contains(true))) {
					lock();
					return;
				}
			}
		}
		unlock();

	}

	private void setDone() {
		mSaveButton.setText(getString(R.string.done));
	}

	private void setFetching() {
		mSaveButton.setText(getString(R.string.fetching_keys));
	}
}
