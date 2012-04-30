package net.shrine.webclient.client.widgets.suggest;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * 
 * @author clint
 * @date Apr 11, 2012
 * @param <S>
 * 
 * basically a side-effecting function RichSuggestionEvent<S> => void
 */
public interface SuggestRowContainer<S extends IsSerializable> extends HasHideablePopup {
	public void fireSuggestionEvent(final RichSuggestionEvent<S> event);
}
