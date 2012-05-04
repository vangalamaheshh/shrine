package net.shrine.webclient.client.widgets;

import net.shrine.webclient.client.QueryBuildingController;
import net.shrine.webclient.client.QueryGroupId;
import net.shrine.webclient.client.domain.Term;
import net.shrine.webclient.client.util.Util;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

/**
 * 
 * @author clint
 * @date Mar 26, 2012
 */
public final class QueryTerm extends Composite {

	private static final QueryTermUiBinder uiBinder = GWT.create(QueryTermUiBinder.class);

	interface QueryTermUiBinder extends UiBinder<Widget, QueryTerm> { }

	@UiField
	SpanElement termSpan;
	
	@UiField
	CloseButton closeButton;
	
	public QueryTerm(final QueryGroupId queryId, final QueryBuildingController controller, final Term term) {
		Util.requireNotNull(queryId);
		Util.requireNotNull(controller);
		Util.requireNotNull(term);
		
		initWidget(uiBinder.createAndBindUi(this));
		
		termSpan.setInnerText(term.getSimpleName());
		
		this.setTitle(term.getPath());
		
		closeButton.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(final ClickEvent event) {
				controller.removeTerm(queryId, term);
			}
		});
	}
}
