package org.jmj.listener;

import javax.servlet.ServletContextEvent;

import org.springframework.web.context.ContextLoaderListener;

public class PathListener extends ContextLoaderListener {

	@Override
	public void contextInitialized(ServletContextEvent event) {
		super.contextInitialized(event);
		event.getServletContext().setAttribute("ctx", event.getServletContext().getContextPath());
	}
}
