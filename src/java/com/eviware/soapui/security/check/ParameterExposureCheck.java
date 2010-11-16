/*
 *  soapUI, copyright (C) 2004-2010 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */
package com.eviware.soapui.security.check;

import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.eviware.soapui.config.ParameterExposureCheckConfig;
import com.eviware.soapui.config.SecurityCheckConfig;
import com.eviware.soapui.config.TestAssertionConfig;
import com.eviware.soapui.impl.rest.RestRequest;
import com.eviware.soapui.impl.support.AbstractHttpRequest;
import com.eviware.soapui.impl.support.http.HttpRequest;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCaseRunner;
import com.eviware.soapui.impl.wsdl.teststeps.HttpResponseMessageExchange;
import com.eviware.soapui.impl.wsdl.teststeps.HttpTestRequestInterface;
import com.eviware.soapui.impl.wsdl.teststeps.HttpTestRequestStep;
import com.eviware.soapui.impl.wsdl.teststeps.HttpTestRequestStepInterface;
import com.eviware.soapui.impl.wsdl.teststeps.RestTestRequestStep;
import com.eviware.soapui.impl.wsdl.teststeps.assertions.TestAssertionRegistry;
import com.eviware.soapui.impl.wsdl.teststeps.assertions.basic.SimpleContainsAssertion;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.iface.MessageExchange;
import com.eviware.soapui.model.testsuite.TestProperty;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.model.testsuite.Assertable.AssertionStatus;
import com.eviware.soapui.security.SecurityTestContext;
import com.eviware.soapui.security.log.SecurityTestLog;
import com.eviware.soapui.security.log.SecurityTestLogMessageEntry;
import com.eviware.soapui.support.components.SimpleForm;
import com.eviware.soapui.support.types.StringToObjectMap;

/**
 * 
 * @author soapui team
 */

public class ParameterExposureCheck extends AbstractSecurityCheck {

	// JTextField minimumCharactersTextField;
	protected JTextField minimumCharactersTextField;

	public static final String TYPE = "ParameterExposureCheck";
	public static final int DEFAULT_MINIMUM_CHARACTER_LENGTH = 5;

	public ParameterExposureCheck(SecurityCheckConfig config, ModelItem parent,
			String icon) {
		super(config, parent, icon);
		monitorApplicable = true;
		if (config == null) {
			config = SecurityCheckConfig.Factory.newInstance();
			ParameterExposureCheckConfig pescc = ParameterExposureCheckConfig.Factory
					.newInstance();
			pescc.setMinimumLength(DEFAULT_MINIMUM_CHARACTER_LENGTH);
			config.setConfig(pescc);
		} 
		if (config.getConfig() == null) {
			ParameterExposureCheckConfig pescc = ParameterExposureCheckConfig.Factory
			.newInstance();
	pescc.setMinimumLength(DEFAULT_MINIMUM_CHARACTER_LENGTH);
	config.setConfig(pescc);
		}
		

		minimumCharactersTextField = new JTextField(
				((ParameterExposureCheckConfig)config.getConfig()).getMinimumLength());
		minimumCharactersTextField.addKeyListener(new MinimumListener());
	}

	@Override
	protected void execute(TestStep testStep, SecurityTestContext context,
			SecurityTestLog securityTestLog) {
		if (acceptsTestStep(testStep)) {
			WsdlTestCaseRunner testCaseRunner = new WsdlTestCaseRunner(
					(WsdlTestCase) testStep.getTestCase(),
					new StringToObjectMap());
			
			testStep.run(testCaseRunner,testCaseRunner.getRunContext());
			analyze(testStep, context, securityTestLog);
		}
	}

	@Override
	public void analyze(TestStep testStep, SecurityTestContext context,
			SecurityTestLog securityTestLog) {
		if (acceptsTestStep(testStep)) {
			HttpTestRequestStepInterface testStepwithProperties = (HttpTestRequestStepInterface) testStep;
			HttpTestRequestInterface<?> request = testStepwithProperties
					.getTestRequest();
			MessageExchange messageExchange = new HttpResponseMessageExchange(
					request);

			Map<String, TestProperty> params;
			
			//It might be a good idea to refactor HttpRequest and TestRequest to avoid things like this)
			
			AbstractHttpRequest<?> httpRequest = testStepwithProperties.getHttpRequest();
			if (httpRequest instanceof HttpRequest ) {
				params = ((HttpRequest)httpRequest).getParams();
			} else {
				params = ((RestRequest)httpRequest).getParams();
			}

			if (getParamsToCheck().isEmpty()) {
				setParamsToCheck(new ArrayList<String>(params.keySet()));
			}
			
			for (String paramName : getParamsToCheck()) {
				TestProperty param = params.get(paramName);

				if (param != null && param.getValue() != null
						&& param.getValue().length() >= getMinimumLength()) {
					TestAssertionConfig assertionConfig = TestAssertionConfig.Factory
							.newInstance();
					assertionConfig.setType(SimpleContainsAssertion.ID);

					SimpleContainsAssertion containsAssertion = (SimpleContainsAssertion) TestAssertionRegistry
							.getInstance().buildAssertion(assertionConfig,
									testStepwithProperties);
					containsAssertion.setToken(param.getValue());

					containsAssertion.assertResponse(messageExchange, context);

					if (containsAssertion.getStatus().equals(
							AssertionStatus.VALID)) {
						securityTestLog
								.addEntry(new SecurityTestLogMessageEntry(
										"Parameter " + param.getName()
												+ " is exposed in the response"));
					}
				}
			}
		}
	}

	public void setMinimumLength(int minimumLength) {
		((ParameterExposureCheckConfig)config.getConfig()).setMinimumLength(minimumLength);
		minimumCharactersTextField.setText(Integer.toString(minimumLength));
	}

	private int getMinimumLength() {
		return ((ParameterExposureCheckConfig)config.getConfig()).getMinimumLength();
	}

	public List<String> getParamsToCheck() {
		return ((ParameterExposureCheckConfig)config.getConfig()).getParamToCheckList();
	}

	public void setParamsToCheck(List<String> params) {
		((ParameterExposureCheckConfig)config.getConfig()).setParamToCheckArray(params
				.toArray(new String[0]));
	}

	@Override
	public boolean acceptsTestStep(TestStep testStep) {
		return testStep instanceof HttpTestRequestStep
				|| testStep instanceof RestTestRequestStep;
	}

	@Override
	public JComponent getComponent() {
		if (panel == null) {
			panel = new JPanel(new BorderLayout());

			form = new SimpleForm();
			form.addSpace(5);

			form.setDefaultTextFieldColumns(50);

			minimumCharactersTextField = form.appendTextField(
					"Minimum Characters:", "Script to use");
			minimumCharactersTextField.setText("" +((ParameterExposureCheckConfig)config.getConfig()).getMinimumLength());
		}
		return panel;
	}

	private class MinimumListener implements KeyListener {

		@Override
		public void keyPressed(KeyEvent arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void keyReleased(KeyEvent arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void keyTyped(KeyEvent ke) {
			char c = ke.getKeyChar();
			if (!Character.isDigit(c))
				ke.consume();
			setMinimumLength(Integer.parseInt(minimumCharactersTextField
					.getText()));
		}
	}

	@Override
	public String getType() {
		return TYPE;
	}

}
