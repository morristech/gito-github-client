package com.dmitrymalkovich.android.githubanalytics.data.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.dmitrymalkovich.android.githubanalytics.R;
import com.dmitrymalkovich.android.githubanalytics.data.source.GithubRepository;
import com.dmitrymalkovich.android.githubanalytics.data.source.Injection;
import com.dmitrymalkovich.android.githubanalytics.data.source.local.RepositoryContract;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.service.RepositoryService;

import java.io.File;
import java.io.IOException;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 * <p>
 * https://developer.android.com/training/sync-adapters/creating-sync-adapter.html
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private static String LOG_TAG = SyncAdapter.class.getSimpleName();
    // Interval at which to sync with the weather, in seconds.
    // 60 seconds (1 minute) * 180 = 3 hours
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SYNC_INTERVAL_IN_MINUTES = 15;
    private static final int SYNC_INTERVAL =
            SYNC_INTERVAL_IN_MINUTES *
                    SECONDS_PER_MINUTE;
    private static final int SYNC_FLEXTIME = SYNC_INTERVAL / 3;
    private static Account mAccount;
    @SuppressWarnings("all")
    ContentResolver mContentResolver;

    /**
     * Set up the sync adapter
     */
    SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        Log.d(LOG_TAG, "SyncAdapter");
        /*
         * If your app uses a content resolver, get an instance of it
         * from the incoming Context
         */
        mContentResolver = context.getContentResolver();
    }

    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    @SuppressWarnings("unused")
    public SyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        Log.d(LOG_TAG, "SyncAdapter");
        /*
         * If your app uses a content resolver, get an instance of it
         * from the incoming Context
         */
        mContentResolver = context.getContentResolver();
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        Log.d(LOG_TAG, "onPerformSync");
        GithubRepository repository = Injection.provideGithubRepository(getContext());
        String token = repository.getToken();
        if (token != null)
        {
            try {
                RepositoryService service = new RepositoryService();
                service.getClient().setOAuth2Token(token);

                for (Repository repo : service.getRepositories()) {

                    ContentValues repositoryValues = new ContentValues();
                    repositoryValues.put(RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_ID,
                            repo.getId());
                    repositoryValues.put(RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_NAME,
                            repo.getName());
                    repositoryValues.put(RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_FULL_NAME,
                            repo.getOwner().getName() + File.separator + repo.getName());
                    repositoryValues.put(RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_DESCRIPTION,
                            repo.getDescription());
                    repositoryValues.put(RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_PRIVATE,
                            repo.isPrivate());
                    repositoryValues.put(RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_FORK,
                            repo.isFork());
                    repositoryValues.put(RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_URL,
                            repo.getUrl());
                    repositoryValues.put(RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_HTML_URL,
                            repo.getHtmlUrl());
                    repositoryValues.put(RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_FORKS,
                            repo.getForks());
                    repositoryValues.put(RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_WATCHERS,
                            repo.getWatchers());
                    repositoryValues.put(RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_LANGUAGE,
                            repo.getLanguage());

                    Cursor cursor = getContext().getContentResolver().query(RepositoryContract.RepositoryEntry.CONTENT_URI,
                            new String[]{RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_ID},
                            RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_ID + " = " + repo.getId(),
                            null,
                            null);
                    if (cursor != null && cursor.moveToFirst()) {
                        getContext().getContentResolver().update(
                                RepositoryContract.RepositoryEntry.CONTENT_URI,
                                repositoryValues,
                                RepositoryContract.RepositoryEntry.COLUMN_REPOSITORY_ID + " = " + repo.getId(),
                                null);
                    }
                    else {
                        getContext().getContentResolver().insert(
                                RepositoryContract.RepositoryEntry.CONTENT_URI,
                                repositoryValues);
                    }
                    if (cursor != null)
                    {
                        cursor.close();
                    }
                }

            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
            }
        }
        else
        {
            Log.d(LOG_TAG, "token does not exists");
        }
    }

    public static void initializeSyncAdapter(Context context) {
        Log.d(LOG_TAG, "initializeSyncAdapter");
        getSyncAccount(context);
    }

    private static void onAccountCreated(Account newAccount, Context context) {
        Log.d(LOG_TAG, "onAccountCreated");
        configurePeriodicSync(context, SYNC_INTERVAL, SYNC_FLEXTIME);
        ContentResolver.setSyncAutomatically(newAccount, context.getString(R.string.sync_authority), true);
        syncImmediately(context);
    }

    /**
     * Helper method to have the sync adapter sync immediately
     *
     * @param context The context used to access the account service
     */
    private static void syncImmediately(Context context) {
        Log.d(LOG_TAG, "syncImmediately");
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        ContentResolver.requestSync(mAccount,
                context.getString(R.string.sync_authority), bundle);
    }

    /**
     * Helper method to schedule the sync adapter periodic execution
     */
    private static void configurePeriodicSync(Context context, int syncInterval, int flexTime) {
        Log.d(LOG_TAG, "configurePeriodicSync");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(syncInterval, flexTime).
                    setSyncAdapter(mAccount, context.getString(R.string.sync_authority)).
                    setExtras(new Bundle()).build();
            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(mAccount,
                    context.getString(R.string.sync_authority), new Bundle(), syncInterval);
        }
    }

    private static Account getSyncAccount(Context context) {
        Log.d(LOG_TAG, "getSyncAccount");
        AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        mAccount = new Account(
                context.getString(R.string.app_name),
                context.getString(R.string.sync_account_type));
        accountManager.addAccountExplicitly(mAccount, "", null);
        onAccountCreated(mAccount, context);
        return mAccount;
    }
}