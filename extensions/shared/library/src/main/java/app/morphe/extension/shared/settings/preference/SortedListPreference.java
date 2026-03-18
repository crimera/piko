package app.morphe.extension.shared.settings.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.shared.Utils;

/**
 * PreferenceList that sorts itself.
 * By default the first entry is preserved in its original position,
 * and all other entries are sorted alphabetically.
 *
 * Ideally the 'keep first entries to preserve' is an xml parameter,
 * but currently that's not so simple since Extensions code cannot use
 * generated code from the Patches repo (which is required for custom xml parameters).
 *
 * If any class wants to use a different getFirstEntriesToPreserve value,
 * it needs to subclass this preference and override {@link #getFirstEntriesToPreserve}.
 */
@SuppressWarnings({"unused", "deprecation"})
public class SortedListPreference extends CustomDialogListPreference {

    /**
     * Sorts the current list entries.
     *
     * @param firstEntriesToPreserve The number of entries to preserve in their original position,
     *                               or a negative value to not sort and leave entries
     *                               as they current are.
     */
    public void sortEntryAndValues(int firstEntriesToPreserve) {
        CharSequence[] entries = getEntries();
        CharSequence[] entryValues = getEntryValues();
        if (entries == null || entryValues == null) {
            return;
        }

        final int entrySize = entries.length;
        if (entrySize != entryValues.length) {
            // Xml array declaration has a missing/extra entry.
            throw new IllegalStateException();
        }

        if (firstEntriesToPreserve < 0) {
            return; // Nothing to do.
        }

        List<Pair<CharSequence, CharSequence>> firstEntries = new ArrayList<>(firstEntriesToPreserve);

        // Android does not have a triple class like Kotlin, So instead use a nested pair.
        // Cannot easily use a SortedMap, because if two entries incorrectly have
        // identical names then the duplicates entries are not preserved.
        List<Pair<String, Pair<CharSequence, CharSequence>>> lastEntries = new ArrayList<>();

        for (int i = 0; i < entrySize; i++) {
            Pair<CharSequence, CharSequence> pair = new Pair<>(entries[i], entryValues[i]);
            if (i < firstEntriesToPreserve) {
                firstEntries.add(pair);
            } else {
                lastEntries.add(new Pair<>(Utils.removePunctuationToLowercase(pair.first), pair));
            }
        }

        //noinspection ComparatorCombinators
        Collections.sort(lastEntries, (pair1, pair2)
                -> pair1.first.compareTo(pair2.first));

        CharSequence[] sortedEntries = new CharSequence[entrySize];
        CharSequence[] sortedEntryValues = new CharSequence[entrySize];

        int i = 0;
        for (Pair<CharSequence, CharSequence> pair : firstEntries) {
            sortedEntries[i] = pair.first;
            sortedEntryValues[i] = pair.second;
            i++;
        }

        for (Pair<String, Pair<CharSequence, CharSequence>> outer : lastEntries) {
            Pair<CharSequence, CharSequence> inner = outer.second;
            sortedEntries[i] = inner.first;
            sortedEntryValues[i] = inner.second;
            i++;
        }

        super.setEntries(sortedEntries);
        super.setEntryValues(sortedEntryValues);
    }

    public SortedListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        sortEntryAndValues(getFirstEntriesToPreserve());
    }

    public SortedListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        sortEntryAndValues(getFirstEntriesToPreserve());
    }

    public SortedListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        sortEntryAndValues(getFirstEntriesToPreserve());
    }

    public SortedListPreference(Context context) {
        super(context);

        sortEntryAndValues(getFirstEntriesToPreserve());
    }

    /**
     * @return The number of first entries to leave exactly where they are, and do not sort them.
     *         A negative value indicates do not sort any entries.
     */
    protected int getFirstEntriesToPreserve() {
        return 1;
    }
}
