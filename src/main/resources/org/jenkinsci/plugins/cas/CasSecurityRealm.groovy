import hudson.security.ChainedServletFilter
import hudson.security.HttpSessionContextIntegrationFilter2
import hudson.security.SecurityRealm

import org.jasig.cas.client.session.HashMapBackedSessionMappingStorage;
import org.jasig.cas.client.session.SingleSignOutHandler
import org.jenkinsci.plugins.cas.spring.CasBeanFactory
import org.jenkinsci.plugins.cas.spring.CasEventListener
import org.jenkinsci.plugins.cas.spring.security.CasAuthenticationEntryPoint
import org.jenkinsci.plugins.cas.spring.security.CasRestAuthenticator
import org.jenkinsci.plugins.cas.spring.security.CasSingleSignOutFilter
import org.jenkinsci.plugins.cas.spring.security.CasUserDetailsService
import org.jenkinsci.plugins.cas.spring.security.DynamicServiceAuthenticationDetailsSource
import org.jenkinsci.plugins.cas.spring.security.SessionUrlAuthenticationSuccessHandler
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.cas.authentication.CasAuthenticationProvider
import org.springframework.security.cas.web.CasAuthenticationFilter
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler


casEventListener(CasEventListener) {
	fullNameAttribute = casProtocol.fullNameAttribute
	emailAttribute = casProtocol.emailAttribute
}

casBeanFactory(CasBeanFactory) {
	casServerUrl = securityRealm.casServerUrl
	casProtocol = casProtocol
}

casServiceProperties(casBeanFactory: "createServiceProperties") {
	sendRenew = securityRealm.forceRenewal
	service = securityRealm.finishLoginUrl
}

casTicketValidator(casBeanFactory: "createTicketValidator") {
	renew = securityRealm.forceRenewal
}

casAuthenticationUserDetailsService(CasUserDetailsService) {
	attributes = casProtocol.authoritiesAttributes
	convertToUpperCase = false
	defaultAuthorities = [ SecurityRealm.AUTHENTICATED_AUTHORITY.getAuthority() ]
}

casAuthenticationManager(ProviderManager) {
	providers = [
		bean(CasAuthenticationProvider) {
			ticketValidator = casTicketValidator
			authenticationUserDetailsService = casAuthenticationUserDetailsService
			key = "cas_auth_provider"
		}
	]
	authenticationEventPublisher = bean(DefaultAuthenticationEventPublisher)
}

casAuthenticationEntryPoint(CasAuthenticationEntryPoint) {
	loginUrl = securityRealm.casServerUrl + "login"
	serviceProperties = casServiceProperties
	targetUrlParameter = "from"
	targetUrlSessionAttribute = SessionUrlAuthenticationSuccessHandler.DEFAULT_TARGET_URL_SESSION_ATTRIBUTE
}

casAuthenticationDetailsSource(DynamicServiceAuthenticationDetailsSource, casServiceProperties)

casSessionMappingStorage(HashMapBackedSessionMappingStorage)

casFilter(ChainedServletFilter) {
	filters = [
		bean(HttpSessionContextIntegrationFilter2) {
			allowSessionCreation = false;
		},
		bean(CasSingleSignOutFilter) {
			enabled = securityRealm.enableSingleSignOut
			filterProcessesUrl = "/" + securityRealm.finishLoginUrl
			singleSignOutHandler = bean(SingleSignOutHandler) {
				artifactParameterName = casProtocol.artifactParameter
				casServerUrlPrefix = securityRealm.casServerUrl
				sessionMappingStorage = casSessionMappingStorage
			}
		},
		bean(CasAuthenticationFilter) {
			filterProcessesUrl = "/" + securityRealm.finishLoginUrl
			authenticationManager = casAuthenticationManager
			authenticationDetailsSource = casAuthenticationDetailsSource
			serviceProperties = casServiceProperties
			authenticationFailureHandler = bean(SimpleUrlAuthenticationFailureHandler, "/" + securityRealm.failedLoginUrl)
			authenticationSuccessHandler = bean(SessionUrlAuthenticationSuccessHandler, "/")
			continueChainBeforeSuccessfulAuthentication = true
		}
	]
}

casRestAuthenticator(CasRestAuthenticator) {
	casServerUrl = securityRealm.casServerUrl
	authenticationManager = casAuthenticationManager
	authenticationDetailsSource = casAuthenticationDetailsSource
}
