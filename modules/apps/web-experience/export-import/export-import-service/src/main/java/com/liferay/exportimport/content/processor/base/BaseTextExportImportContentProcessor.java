/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.exportimport.content.processor.base;

import com.liferay.document.library.kernel.exception.NoSuchFileEntryException;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.service.DLAppLocalServiceUtil;
import com.liferay.document.library.kernel.service.DLFileEntryLocalServiceUtil;
import com.liferay.document.library.kernel.util.DLUtil;
import com.liferay.exportimport.configuration.ExportImportServiceConfiguration;
import com.liferay.exportimport.content.processor.ExportImportContentProcessor;
import com.liferay.exportimport.kernel.lar.ExportImportPathUtil;
import com.liferay.exportimport.kernel.lar.PortletDataContext;
import com.liferay.exportimport.kernel.lar.PortletDataHandlerKeys;
import com.liferay.exportimport.kernel.lar.StagedModelDataHandlerUtil;
import com.liferay.petra.string.CharPool;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.NoSuchLayoutException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.GroupConstants;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutFriendlyURL;
import com.liferay.portal.kernel.model.LayoutSet;
import com.liferay.portal.kernel.model.StagedModel;
import com.liferay.portal.kernel.model.VirtualLayoutConstants;
import com.liferay.portal.kernel.module.configuration.ConfigurationProviderUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.service.GroupLocalServiceUtil;
import com.liferay.portal.kernel.service.LayoutFriendlyURLLocalServiceUtil;
import com.liferay.portal.kernel.service.LayoutLocalServiceUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.PortletKeys;
import com.liferay.portal.kernel.util.PredicateFilter;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.util.PropsValues;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author     Gergely Mathe
 * @deprecated As of 4.0.0
 */
@Deprecated
public class BaseTextExportImportContentProcessor
	implements ExportImportContentProcessor<String> {

	@Override
	public String replaceExportContentReferences(
			PortletDataContext portletDataContext, StagedModel stagedModel,
			String content, boolean exportReferencedContent,
			boolean escapeContent)
		throws Exception {

		content = replaceExportDLReferences(
			portletDataContext, stagedModel, content, exportReferencedContent);

		content = replaceExportLayoutReferences(
			portletDataContext, stagedModel, content);

		content = replaceExportLinksToLayouts(
			portletDataContext, stagedModel, content);

		if (escapeContent) {
			content = StringUtil.replace(
				content, StringPool.AMPERSAND_ENCODED, StringPool.AMPERSAND);
		}

		return content;
	}

	@Override
	public String replaceImportContentReferences(
			PortletDataContext portletDataContext, StagedModel stagedModel,
			String content)
		throws Exception {

		content = replaceImportDLReferences(
			portletDataContext, stagedModel, content);

		content = replaceImportLayoutReferences(portletDataContext, content);
		content = replaceImportLinksToLayouts(portletDataContext, content);

		return content;
	}

	@Override
	public void validateContentReferences(long groupId, String content)
		throws PortalException {

		validateDLReferences(groupId, content);
		validateLayoutReferences(groupId, content);
		validateLinksToLayoutsReferences(content);
	}

	protected void deleteTimestampParameters(StringBuilder sb, int beginPos) {
		beginPos = sb.indexOf(StringPool.CLOSE_BRACKET, beginPos);

		if ((beginPos == -1) || (beginPos == (sb.length() - 1)) ||
			(sb.charAt(beginPos + 1) != CharPool.QUESTION)) {

			return;
		}

		int endPos = StringUtil.indexOfAny(
			sb.toString(), DL_REFERENCE_LEGACY_STOP_CHARS, beginPos + 2);

		if (endPos == -1) {
			return;
		}

		String urlParams = sb.substring(beginPos + 1, endPos);

		urlParams = HttpUtil.removeParameter(urlParams, "t");

		sb.replace(beginPos + 1, endPos, urlParams);
	}

	protected Map<String, String[]> getDLReferenceParameters(
		long groupId, String content, int beginPos, int endPos) {

		boolean legacyURL = true;
		char[] stopChars = DL_REFERENCE_LEGACY_STOP_CHARS;

		if (content.startsWith("/documents/", beginPos)) {
			legacyURL = false;
			stopChars = DL_REFERENCE_STOP_CHARS;
		}

		endPos = StringUtil.indexOfAny(content, stopChars, beginPos, endPos);

		if (endPos == -1) {
			return null;
		}

		Map<String, String[]> map = new HashMap<>();

		String dlReference = content.substring(beginPos, endPos);

		while (dlReference.contains(StringPool.AMPERSAND_ENCODED)) {
			dlReference = dlReference.replace(
				StringPool.AMPERSAND_ENCODED, StringPool.AMPERSAND);
		}

		if (!legacyURL) {
			String[] pathArray = dlReference.split(StringPool.SLASH);

			if (pathArray.length < 3) {
				return map;
			}

			map.put("groupId", new String[] {pathArray[2]});

			if (pathArray.length == 4) {
				map.put("uuid", new String[] {pathArray[3]});
			}
			else if (pathArray.length == 5) {
				map.put("folderId", new String[] {pathArray[3]});
				map.put(
					"title", new String[] {HttpUtil.decodeURL(pathArray[4])});
			}
			else if (pathArray.length > 5) {
				map.put("uuid", new String[] {pathArray[5]});
			}
		}
		else {
			dlReference = dlReference.substring(
				dlReference.indexOf(CharPool.QUESTION) + 1);

			map = HttpUtil.parameterMapFromString(dlReference);

			String[] imageIds = null;

			if (map.containsKey("img_id")) {
				imageIds = map.get("img_id");
			}
			else if (map.containsKey("i_id")) {
				imageIds = map.get("i_id");
			}

			imageIds = ArrayUtil.filter(
				imageIds,
				new PredicateFilter<String>() {

					@Override
					public boolean filter(String imageId) {
						if (Validator.isNotNull(imageId)) {
							return true;
						}

						return false;
					}

				});

			if (ArrayUtil.isNotEmpty(imageIds)) {
				map.put("image_id", imageIds);
			}
		}

		map.put("endPos", new String[] {String.valueOf(endPos)});

		String groupIdString = MapUtil.getString(map, "groupId");

		if (groupIdString.equals("@group_id@")) {
			groupIdString = String.valueOf(groupId);

			map.put("groupId", new String[] {groupIdString});
		}

		return map;
	}

	protected FileEntry getFileEntry(Map<String, String[]> map) {
		if (MapUtil.isEmpty(map)) {
			return null;
		}

		FileEntry fileEntry = null;

		try {
			String uuid = MapUtil.getString(map, "uuid");
			long groupId = MapUtil.getLong(map, "groupId");

			if (Validator.isNotNull(uuid)) {
				fileEntry = DLAppLocalServiceUtil.getFileEntryByUuidAndGroupId(
					uuid, groupId);
			}
			else {
				if (map.containsKey("folderId")) {
					long folderId = MapUtil.getLong(map, "folderId");
					String name = MapUtil.getString(map, "name");
					String title = MapUtil.getString(map, "title");

					if (Validator.isNotNull(title)) {
						fileEntry = DLAppLocalServiceUtil.getFileEntry(
							groupId, folderId, title);
					}
					else {
						DLFileEntry dlFileEntry =
							DLFileEntryLocalServiceUtil.fetchFileEntryByName(
								groupId, folderId, name);

						if (dlFileEntry != null) {
							fileEntry = DLAppLocalServiceUtil.getFileEntry(
								dlFileEntry.getFileEntryId());
						}
					}
				}
				else if (map.containsKey("image_id")) {
					DLFileEntry dlFileEntry =
						DLFileEntryLocalServiceUtil.fetchFileEntryByAnyImageId(
							MapUtil.getLong(map, "image_id"));

					if (dlFileEntry != null) {
						fileEntry = DLAppLocalServiceUtil.getFileEntry(
							dlFileEntry.getFileEntryId());
					}
				}
			}
		}
		catch (Exception e) {
			if (_log.isDebugEnabled()) {
				_log.debug(e, e);
			}
			else if (_log.isWarnEnabled()) {
				_log.warn(e.getMessage());
			}
		}

		return fileEntry;
	}

	protected boolean isValidateLayoutReferences() throws PortalException {
		long companyId = CompanyThreadLocal.getCompanyId();

		ExportImportServiceConfiguration exportImportServiceConfiguration =
			ConfigurationProviderUtil.getCompanyConfiguration(
				ExportImportServiceConfiguration.class, companyId);

		return exportImportServiceConfiguration.validateLayoutReferences();
	}

	protected String replaceExportDLReferences(
			PortletDataContext portletDataContext, StagedModel stagedModel,
			String content, boolean exportReferencedContent)
		throws Exception {

		Group group = GroupLocalServiceUtil.getGroup(
			portletDataContext.getGroupId());

		if (group.isStagingGroup()) {
			group = group.getLiveGroup();
		}

		if (group.isStaged() && !group.isStagedRemotely() &&
			!group.isStagedPortlet(PortletKeys.DOCUMENT_LIBRARY)) {

			return content;
		}

		StringBuilder sb = new StringBuilder(content);

		String contextPath = PortalUtil.getPathContext();

		String[] patterns = {
			contextPath.concat("/c/document_library/get_file?"),
			contextPath.concat("/documents/"),
			contextPath.concat("/image/image_gallery?")
		};

		int beginPos = -1;
		int endPos = content.length();

		while (true) {
			beginPos = StringUtil.lastIndexOfAny(content, patterns, endPos);

			if (beginPos == -1) {
				break;
			}

			Map<String, String[]> dlReferenceParameters =
				getDLReferenceParameters(
					portletDataContext.getScopeGroupId(), content,
					beginPos + contextPath.length(), endPos);

			FileEntry fileEntry = getFileEntry(dlReferenceParameters);

			if (fileEntry == null) {
				endPos = beginPos - 1;

				continue;
			}

			endPos = MapUtil.getInteger(dlReferenceParameters, "endPos");

			try {
				if (exportReferencedContent && !fileEntry.isInTrash()) {
					StagedModelDataHandlerUtil.exportReferenceStagedModel(
						portletDataContext, stagedModel, fileEntry,
						PortletDataContext.REFERENCE_TYPE_DEPENDENCY);
				}
				else {
					Element entityElement =
						portletDataContext.getExportDataElement(stagedModel);

					String referenceType =
						PortletDataContext.REFERENCE_TYPE_DEPENDENCY;

					if (fileEntry.isInTrash()) {
						referenceType =
							PortletDataContext.
								REFERENCE_TYPE_DEPENDENCY_DISPOSABLE;
					}

					portletDataContext.addReferenceElement(
						stagedModel, entityElement, fileEntry, referenceType,
						true);
				}

				String path = ExportImportPathUtil.getModelPath(fileEntry);

				StringBundler exportedReferenceSB = new StringBundler(6);

				exportedReferenceSB.append("[$dl-reference=");
				exportedReferenceSB.append(path);
				exportedReferenceSB.append("$]");

				if (fileEntry.isInTrash()) {
					String originalReference = sb.substring(beginPos, endPos);

					exportedReferenceSB.append("[#dl-reference=");
					exportedReferenceSB.append(originalReference);
					exportedReferenceSB.append("#]");
				}

				sb.replace(beginPos, endPos, exportedReferenceSB.toString());

				int deleteTimestampParametersOffset = beginPos;

				if (fileEntry.isInTrash()) {
					deleteTimestampParametersOffset = sb.indexOf(
						"[#dl-reference=", beginPos);
				}

				deleteTimestampParameters(sb, deleteTimestampParametersOffset);
			}
			catch (Exception e) {
				if (_log.isDebugEnabled()) {
					_log.debug(e, e);
				}
				else if (_log.isWarnEnabled()) {
					StringBundler exceptionSB = new StringBundler(6);

					exceptionSB.append("Unable to process file entry ");
					exceptionSB.append(fileEntry.getFileEntryId());
					exceptionSB.append(" for staged model ");
					exceptionSB.append(stagedModel.getModelClassName());
					exceptionSB.append(" with primary key ");
					exceptionSB.append(stagedModel.getPrimaryKeyObj());

					_log.warn(exceptionSB.toString());
				}
			}

			endPos = beginPos - 1;
		}

		return sb.toString();
	}

	protected String replaceExportHostname(
			long groupId, String url, StringBundler urlSB)
		throws PortalException {

		if (!HttpUtil.hasProtocol(url)) {
			return url;
		}

		boolean secure = HttpUtil.isSecure(url);

		int serverPort = PortalUtil.getPortalServerPort(secure);

		if (serverPort == -1) {
			return url;
		}

		Group group = GroupLocalServiceUtil.getGroup(groupId);

		LayoutSet publicLayoutSet = group.getPublicLayoutSet();

		String publicLayoutSetVirtualHostname =
			publicLayoutSet.getVirtualHostname();

		String portalUrl = StringPool.BLANK;

		if (Validator.isNotNull(publicLayoutSetVirtualHostname)) {
			portalUrl = PortalUtil.getPortalURL(
				publicLayoutSetVirtualHostname, serverPort, secure);

			if (url.startsWith(portalUrl)) {
				if (secure) {
					urlSB.append(DATA_HANDLER_PUBLIC_LAYOUT_SET_SECURE_URL);
				}
				else {
					urlSB.append(DATA_HANDLER_PUBLIC_LAYOUT_SET_URL);
				}

				return url.substring(portalUrl.length());
			}
		}

		LayoutSet privateLayoutSet = group.getPrivateLayoutSet();

		String privateLayoutSetVirtualHostname =
			privateLayoutSet.getVirtualHostname();

		if (Validator.isNotNull(privateLayoutSetVirtualHostname)) {
			portalUrl = PortalUtil.getPortalURL(
				privateLayoutSetVirtualHostname, serverPort, secure);

			if (url.startsWith(portalUrl)) {
				if (secure) {
					urlSB.append(DATA_HANDLER_PRIVATE_LAYOUT_SET_SECURE_URL);
				}
				else {
					urlSB.append(DATA_HANDLER_PRIVATE_LAYOUT_SET_URL);
				}

				return url.substring(portalUrl.length());
			}
		}

		Company company = CompanyLocalServiceUtil.getCompany(
			group.getCompanyId());

		String companyVirtualHostname = company.getVirtualHostname();

		if (Validator.isNotNull(companyVirtualHostname)) {
			portalUrl = PortalUtil.getPortalURL(
				companyVirtualHostname, serverPort, secure);

			if (url.startsWith(portalUrl)) {
				if (secure) {
					urlSB.append(DATA_HANDLER_COMPANY_SECURE_URL);
				}
				else {
					urlSB.append(DATA_HANDLER_COMPANY_URL);
				}

				return url.substring(portalUrl.length());
			}
		}

		portalUrl = PortalUtil.getPortalURL("localhost", serverPort, secure);

		if (url.startsWith(portalUrl)) {
			return url.substring(portalUrl.length());
		}

		return url;
	}

	protected String replaceExportLayoutReferences(
			PortletDataContext portletDataContext, StagedModel stagedModel,
			String content)
		throws Exception {

		Group group = GroupLocalServiceUtil.getGroup(
			portletDataContext.getScopeGroupId());

		StringBuilder sb = new StringBuilder(content);

		String[] patterns = {"href=", "[["};

		int beginPos = -1;
		int endPos = content.length();
		int offset = 0;

		while (true) {
			if (beginPos > -1) {
				endPos = beginPos - 1;
			}

			beginPos = StringUtil.lastIndexOfAny(content, patterns, endPos);

			if (beginPos == -1) {
				break;
			}

			if (content.startsWith("href=", beginPos)) {
				offset = 5;

				char c = content.charAt(beginPos + offset);

				if ((c == CharPool.APOSTROPHE) || (c == CharPool.QUOTE)) {
					offset++;
				}
			}
			else if (content.charAt(beginPos) == CharPool.OPEN_BRACKET) {
				offset = 2;
			}

			endPos = StringUtil.indexOfAny(
				content, LAYOUT_REFERENCE_STOP_CHARS, beginPos + offset,
				endPos);

			if (endPos == -1) {
				continue;
			}

			String url = content.substring(beginPos + offset, endPos);

			if (url.endsWith(StringPool.SLASH)) {
				url = url.substring(0, url.length() - 1);
			}

			StringBundler urlSB = new StringBundler(6);

			try {
				url = replaceExportHostname(
					portletDataContext.getScopeGroupId(), url, urlSB);

				if (!url.startsWith(StringPool.SLASH)) {
					continue;
				}

				String pathContext = PortalUtil.getPathContext();

				if (pathContext.length() > 1) {
					if (!url.startsWith(pathContext)) {
						continue;
					}

					urlSB.append(DATA_HANDLER_PATH_CONTEXT);

					url = url.substring(pathContext.length());
				}

				if (!url.startsWith(StringPool.SLASH)) {
					continue;
				}

				int pos = url.indexOf(StringPool.SLASH, 1);

				String localePath = StringPool.BLANK;

				Locale locale = null;

				if (pos != -1) {
					localePath = url.substring(0, pos);

					locale = LocaleUtil.fromLanguageId(
						localePath.substring(1), true, false);
				}

				if (locale != null) {
					String urlWithoutLocale = url.substring(
						localePath.length());

					if (urlWithoutLocale.startsWith(
							PRIVATE_GROUP_SERVLET_MAPPING) ||
						urlWithoutLocale.startsWith(
							PRIVATE_USER_SERVLET_MAPPING) ||
						urlWithoutLocale.startsWith(
							PUBLIC_GROUP_SERVLET_MAPPING)) {

						urlSB.append(localePath);

						url = urlWithoutLocale;
					}
				}

				boolean privateLayout = false;

				if (url.startsWith(PRIVATE_GROUP_SERVLET_MAPPING)) {
					urlSB.append(DATA_HANDLER_PRIVATE_GROUP_SERVLET_MAPPING);

					url = url.substring(
						PRIVATE_GROUP_SERVLET_MAPPING.length() - 1);

					privateLayout = true;
				}
				else if (url.startsWith(PRIVATE_USER_SERVLET_MAPPING)) {
					urlSB.append(DATA_HANDLER_PRIVATE_USER_SERVLET_MAPPING);

					url = url.substring(
						PRIVATE_USER_SERVLET_MAPPING.length() - 1);

					privateLayout = true;
				}
				else if (url.startsWith(PUBLIC_GROUP_SERVLET_MAPPING)) {
					urlSB.append(DATA_HANDLER_PUBLIC_SERVLET_MAPPING);

					url = url.substring(
						PUBLIC_GROUP_SERVLET_MAPPING.length() - 1);
				}
				else {
					String urlSBString = urlSB.toString();

					LayoutSet layoutSet = null;

					if (urlSBString.contains(
							DATA_HANDLER_PUBLIC_LAYOUT_SET_SECURE_URL) ||
						urlSBString.contains(
							DATA_HANDLER_PUBLIC_LAYOUT_SET_URL)) {

						layoutSet = group.getPublicLayoutSet();
					}
					else if (urlSBString.contains(
								DATA_HANDLER_PRIVATE_LAYOUT_SET_SECURE_URL) ||
							 urlSBString.contains(
								 DATA_HANDLER_PRIVATE_LAYOUT_SET_URL)) {

						layoutSet = group.getPrivateLayoutSet();
					}

					if (layoutSet == null) {
						continue;
					}

					privateLayout = layoutSet.isPrivateLayout();

					LayoutFriendlyURL layoutFriendlyUrl =
						LayoutFriendlyURLLocalServiceUtil.
							fetchFirstLayoutFriendlyURL(
								group.getGroupId(), privateLayout, url);

					if (layoutFriendlyUrl == null) {
						continue;
					}

					if (privateLayout) {
						if (group.isUser()) {
							urlSB.append(
								DATA_HANDLER_PRIVATE_USER_SERVLET_MAPPING);
						}
						else {
							urlSB.append(
								DATA_HANDLER_PRIVATE_GROUP_SERVLET_MAPPING);
						}
					}
					else {
						urlSB.append(DATA_HANDLER_PUBLIC_SERVLET_MAPPING);
					}

					urlSB.append(DATA_HANDLER_GROUP_FRIENDLY_URL);

					continue;
				}

				long groupId = group.getGroupId();

				Layout layout = LayoutLocalServiceUtil.fetchLayoutByFriendlyURL(
					groupId, privateLayout, url);

				if (layout != null) {
					Element entityElement =
						portletDataContext.getExportDataElement(stagedModel);

					portletDataContext.addReferenceElement(
						stagedModel, entityElement, layout,
						PortletDataContext.REFERENCE_TYPE_DEPENDENCY, true);

					continue;
				}

				pos = url.indexOf(StringPool.SLASH, 1);

				String groupFriendlyURL = url;

				if (pos != -1) {
					groupFriendlyURL = url.substring(0, pos);
				}

				Group urlGroup = GroupLocalServiceUtil.fetchFriendlyURLGroup(
					group.getCompanyId(), groupFriendlyURL);

				if (urlGroup == null) {
					throw new NoSuchLayoutException();
				}

				urlSB.append(DATA_HANDLER_GROUP_FRIENDLY_URL);

				// Append the UUID. This information will be used during the
				// import process when looking up the proper group for the link.

				urlSB.append(StringPool.AT);

				if (urlGroup.isStagedRemotely()) {
					String remoteGroupUuid = urlGroup.getTypeSettingsProperty(
						"remoteGroupUUID");

					if (Validator.isNotNull(remoteGroupUuid)) {
						urlSB.append(remoteGroupUuid);
					}
				}
				else if (urlGroup.isStaged()) {
					Group liveGroup = urlGroup.getLiveGroup();

					urlSB.append(liveGroup.getUuid());
				}
				else if (group.getGroupId() == urlGroup.getGroupId()) {
					urlSB.append(urlGroup.getFriendlyURL());
				}
				else {
					urlSB.append(urlGroup.getUuid());
				}

				urlSB.append(StringPool.AT);

				String siteAdminURL =
					GroupConstants.CONTROL_PANEL_FRIENDLY_URL +
						PropsValues.CONTROL_PANEL_LAYOUT_FRIENDLY_URL;

				if (url.endsWith(siteAdminURL)) {
					urlSB.append(DATA_HANDLER_SITE_ADMIN_URL);

					url = StringPool.BLANK;

					continue;
				}

				if (pos == -1) {
					url = StringPool.BLANK;

					continue;
				}

				url = url.substring(pos);

				layout = LayoutLocalServiceUtil.getFriendlyURLLayout(
					urlGroup.getGroupId(), privateLayout, url);

				Element entityElement = portletDataContext.getExportDataElement(
					stagedModel);

				portletDataContext.addReferenceElement(
					stagedModel, entityElement, layout,
					PortletDataContext.REFERENCE_TYPE_DEPENDENCY, true);
			}
			catch (Exception e) {
				if (e instanceof NoSuchLayoutException &&
					!isValidateLayoutReferences()) {

					continue;
				}

				if (_log.isDebugEnabled()) {
					_log.debug(e, e);
				}
				else if (_log.isWarnEnabled()) {
					StringBundler exceptionSB = new StringBundler(6);

					exceptionSB.append("Unable to process layout URL ");
					exceptionSB.append(url);
					exceptionSB.append(" for staged model ");
					exceptionSB.append(stagedModel.getModelClassName());
					exceptionSB.append(" with primary key ");
					exceptionSB.append(stagedModel.getPrimaryKeyObj());

					_log.warn(exceptionSB.toString());
				}
			}
			finally {
				if (urlSB.length() > 0) {
					urlSB.append(url);

					url = urlSB.toString();
				}

				sb.replace(beginPos + offset, endPos, url);
			}
		}

		return sb.toString();
	}

	protected String replaceExportLinksToLayouts(
			PortletDataContext portletDataContext, StagedModel stagedModel,
			String content)
		throws Exception {

		List<String> oldLinksToLayout = new ArrayList<>();
		List<String> newLinksToLayout = new ArrayList<>();

		Matcher matcher = exportLinksToLayoutPattern.matcher(content);

		while (matcher.find()) {
			long layoutId = GetterUtil.getLong(matcher.group(1));

			String type = matcher.group(2);

			boolean privateLayout = type.startsWith("private");

			try {
				Layout layout = LayoutLocalServiceUtil.getLayout(
					portletDataContext.getScopeGroupId(), privateLayout,
					layoutId);

				String oldLinkToLayout = matcher.group(0);

				StringBundler sb = new StringBundler(3);

				sb.append(type);
				sb.append(StringPool.AT);
				sb.append(layout.getPlid());

				String newLinkToLayout = StringUtil.replace(
					oldLinkToLayout, type, sb.toString());

				oldLinksToLayout.add(oldLinkToLayout);
				newLinksToLayout.add(newLinkToLayout);

				Element entityElement = portletDataContext.getExportDataElement(
					stagedModel);

				portletDataContext.addReferenceElement(
					stagedModel, entityElement, layout,
					PortletDataContext.REFERENCE_TYPE_DEPENDENCY, true);
			}
			catch (Exception e) {
				if (_log.isDebugEnabled() || _log.isWarnEnabled()) {
					String message = StringBundler.concat(
						"Unable to get layout with ID ",
						String.valueOf(layoutId), " in group ",
						String.valueOf(portletDataContext.getScopeGroupId()));

					if (_log.isDebugEnabled()) {
						_log.debug(message, e);
					}
					else {
						_log.warn(message);
					}
				}
			}
		}

		content = StringUtil.replace(
			content, ArrayUtil.toStringArray(oldLinksToLayout.toArray()),
			ArrayUtil.toStringArray(newLinksToLayout.toArray()));

		return content;
	}

	protected String replaceImportDLReferences(
			PortletDataContext portletDataContext, StagedModel stagedModel,
			String content)
		throws Exception {

		List<Element> referenceElements =
			portletDataContext.getReferenceElements(
				stagedModel, DLFileEntry.class);

		for (Element referenceElement : referenceElements) {
			long classPK = GetterUtil.getLong(
				referenceElement.attributeValue("class-pk"));

			Element referenceDataElement =
				portletDataContext.getReferenceDataElement(
					stagedModel, DLFileEntry.class, classPK);

			String path = null;

			if (referenceDataElement != null) {
				path = referenceDataElement.attributeValue("path");
			}

			long groupId = GetterUtil.getLong(
				referenceElement.attributeValue("group-id"));

			if (Validator.isNull(path)) {
				String className = referenceElement.attributeValue(
					"class-name");

				path = ExportImportPathUtil.getModelPath(
					groupId, className, classPK);
			}

			if (!content.contains("[$dl-reference=" + path + "$]")) {
				continue;
			}

			try {
				StagedModelDataHandlerUtil.importReferenceStagedModel(
					portletDataContext, stagedModel, DLFileEntry.class,
					classPK);
			}
			catch (Exception e) {
				if (_log.isDebugEnabled()) {
					_log.debug(e, e);
				}
				else if (_log.isWarnEnabled()) {
					StringBundler sb = new StringBundler(6);

					sb.append("Unable to process file entry ");
					sb.append(classPK);
					sb.append(" for ");
					sb.append(stagedModel.getModelClassName());
					sb.append(" with primary key ");
					sb.append(stagedModel.getPrimaryKeyObj());

					_log.warn(sb.toString());
				}
			}

			Map<Long, Long> dlFileEntryIds =
				(Map<Long, Long>)portletDataContext.getNewPrimaryKeysMap(
					DLFileEntry.class);

			long fileEntryId = MapUtil.getLong(
				dlFileEntryIds, classPK, classPK);

			int beginPos = content.indexOf("[$dl-reference=" + path);

			int endPos = content.indexOf("$]", beginPos) + 2;

			FileEntry importedFileEntry = null;

			try {
				importedFileEntry = DLAppLocalServiceUtil.getFileEntry(
					fileEntryId);
			}
			catch (PortalException pe) {
				if (_log.isDebugEnabled()) {
					_log.debug(pe, pe);
				}
				else if (_log.isWarnEnabled()) {
					_log.warn(pe.getMessage());
				}

				if (content.startsWith("[#dl-reference=", endPos)) {
					int prefixPos = endPos + "[#dl-reference=".length();

					int postfixPos = content.indexOf("#]", prefixPos);

					String originalReference = content.substring(
						prefixPos, postfixPos);

					String exportedReference = content.substring(
						beginPos, postfixPos + 2);

					content = StringUtil.replace(
						content, exportedReference, originalReference);
				}

				continue;
			}

			String url = DLUtil.getPreviewURL(
				importedFileEntry, importedFileEntry.getFileVersion(), null,
				StringPool.BLANK, false, false);

			if (url.contains(StringPool.QUESTION)) {
				content = StringUtil.replace(content, "$]?", "$]&");
			}

			String exportedReference = "[$dl-reference=" + path + "$]";

			if (content.startsWith("[#dl-reference=", endPos)) {
				endPos = content.indexOf("#]", beginPos) + 2;

				exportedReference = content.substring(beginPos, endPos);
			}

			content = StringUtil.replace(content, exportedReference, url);
		}

		return content;
	}

	protected String replaceImportLayoutReferences(
			PortletDataContext portletDataContext, String content)
		throws Exception {

		String companyPortalURL = StringPool.BLANK;
		String privateLayoutSetPortalURL = StringPool.BLANK;
		String publicLayoutSetPortalURL = StringPool.BLANK;

		Group group = GroupLocalServiceUtil.getGroup(
			portletDataContext.getScopeGroupId());

		Company company = CompanyLocalServiceUtil.getCompany(
			group.getCompanyId());

		LayoutSet privateLayoutSet = group.getPrivateLayoutSet();
		LayoutSet publicLayoutSet = group.getPublicLayoutSet();

		int serverPort = PortalUtil.getPortalServerPort(false);

		if (serverPort != -1) {
			if (Validator.isNotNull(company.getVirtualHostname())) {
				companyPortalURL = PortalUtil.getPortalURL(
					company.getVirtualHostname(), serverPort, false);
			}

			if (Validator.isNotNull(privateLayoutSet.getVirtualHostname())) {
				privateLayoutSetPortalURL = PortalUtil.getPortalURL(
					privateLayoutSet.getVirtualHostname(), serverPort, false);
			}

			if (Validator.isNotNull(publicLayoutSet.getVirtualHostname())) {
				publicLayoutSetPortalURL = PortalUtil.getPortalURL(
					publicLayoutSet.getVirtualHostname(), serverPort, false);
			}
		}

		int secureSecurePort = PortalUtil.getPortalServerPort(true);

		String companySecurePortalURL = StringPool.BLANK;
		String privateLayoutSetSecurePortalURL = StringPool.BLANK;
		String publicLayoutSetSecurePortalURL = StringPool.BLANK;

		if (secureSecurePort != -1) {
			if (Validator.isNotNull(company.getVirtualHostname())) {
				companySecurePortalURL = PortalUtil.getPortalURL(
					company.getVirtualHostname(), secureSecurePort, true);
			}

			if (Validator.isNotNull(privateLayoutSet.getVirtualHostname())) {
				privateLayoutSetSecurePortalURL = PortalUtil.getPortalURL(
					privateLayoutSet.getVirtualHostname(), secureSecurePort,
					true);
			}

			if (Validator.isNotNull(publicLayoutSet.getVirtualHostname())) {
				publicLayoutSetSecurePortalURL = PortalUtil.getPortalURL(
					publicLayoutSet.getVirtualHostname(), secureSecurePort,
					true);
			}
		}

		StringBundler sb = new StringBundler(3);

		sb.append(VirtualLayoutConstants.CANONICAL_URL_SEPARATOR);
		sb.append(GroupConstants.CONTROL_PANEL_FRIENDLY_URL);
		sb.append(PropsValues.CONTROL_PANEL_LAYOUT_FRIENDLY_URL);

		content = StringUtil.replace(
			content, DATA_HANDLER_COMPANY_SECURE_URL, companySecurePortalURL);
		content = StringUtil.replace(
			content, DATA_HANDLER_COMPANY_URL, companyPortalURL);

		// Group friendly URLs

		while (true) {
			int groupFriendlyUrlPos = content.indexOf(
				DATA_HANDLER_GROUP_FRIENDLY_URL);

			if (groupFriendlyUrlPos == -1) {
				break;
			}

			int groupUuidPos =
				groupFriendlyUrlPos + DATA_HANDLER_GROUP_FRIENDLY_URL.length();

			int endIndex = content.indexOf(StringPool.AT, groupUuidPos + 1);

			if (endIndex < (groupUuidPos + 1)) {
				content = StringUtil.replaceFirst(
					content, DATA_HANDLER_GROUP_FRIENDLY_URL, StringPool.BLANK,
					groupFriendlyUrlPos);

				continue;
			}

			String groupUuid = content.substring(groupUuidPos + 1, endIndex);

			Group groupFriendlyUrlGroup =
				GroupLocalServiceUtil.fetchGroupByUuidAndCompanyId(
					groupUuid, portletDataContext.getCompanyId());

			if ((groupFriendlyUrlGroup == null) ||
				groupUuid.startsWith(StringPool.SLASH)) {

				content = StringUtil.replaceFirst(
					content, DATA_HANDLER_GROUP_FRIENDLY_URL,
					group.getFriendlyURL(), groupFriendlyUrlPos);
				content = StringUtil.replaceFirst(
					content, StringPool.AT + groupUuid + StringPool.AT,
					StringPool.BLANK, content.indexOf(group.getFriendlyURL()));

				continue;
			}

			content = StringUtil.replaceFirst(
				content, DATA_HANDLER_GROUP_FRIENDLY_URL, StringPool.BLANK,
				groupFriendlyUrlPos);
			content = StringUtil.replaceFirst(
				content, StringPool.AT + groupUuid + StringPool.AT,
				groupFriendlyUrlGroup.getFriendlyURL(), groupFriendlyUrlPos);
		}

		content = StringUtil.replace(
			content, DATA_HANDLER_PATH_CONTEXT, PortalUtil.getPathContext());
		content = StringUtil.replace(
			content, DATA_HANDLER_PRIVATE_GROUP_SERVLET_MAPPING,
			PropsValues.LAYOUT_FRIENDLY_URL_PRIVATE_GROUP_SERVLET_MAPPING);
		content = StringUtil.replace(
			content, DATA_HANDLER_PRIVATE_LAYOUT_SET_SECURE_URL,
			privateLayoutSetSecurePortalURL);
		content = StringUtil.replace(
			content, DATA_HANDLER_PRIVATE_LAYOUT_SET_URL,
			privateLayoutSetPortalURL);
		content = StringUtil.replace(
			content, DATA_HANDLER_PRIVATE_USER_SERVLET_MAPPING,
			PropsValues.LAYOUT_FRIENDLY_URL_PRIVATE_USER_SERVLET_MAPPING);
		content = StringUtil.replace(
			content, DATA_HANDLER_PUBLIC_LAYOUT_SET_SECURE_URL,
			publicLayoutSetSecurePortalURL);
		content = StringUtil.replace(
			content, DATA_HANDLER_PUBLIC_LAYOUT_SET_URL,
			publicLayoutSetPortalURL);
		content = StringUtil.replace(
			content, DATA_HANDLER_PUBLIC_SERVLET_MAPPING,
			PropsValues.LAYOUT_FRIENDLY_URL_PUBLIC_SERVLET_MAPPING);
		content = StringUtil.replace(
			content, DATA_HANDLER_SITE_ADMIN_URL, sb.toString());

		return content;
	}

	protected String replaceImportLinksToLayouts(
			PortletDataContext portletDataContext, String content)
		throws Exception {

		List<String> oldLinksToLayout = new ArrayList<>();
		List<String> newLinksToLayout = new ArrayList<>();

		Matcher matcher = importLinksToLayoutPattern.matcher(content);

		Map<Long, Long> layoutPlids =
			(Map<Long, Long>)portletDataContext.getNewPrimaryKeysMap(
				Layout.class);

		String layoutsImportMode = MapUtil.getString(
			portletDataContext.getParameterMap(),
			PortletDataHandlerKeys.LAYOUTS_IMPORT_MODE,
			PortletDataHandlerKeys.LAYOUTS_IMPORT_MODE_MERGE_BY_LAYOUT_UUID);

		while (matcher.find()) {
			long oldPlid = GetterUtil.getLong(matcher.group(4));

			Long newPlid = MapUtil.getLong(layoutPlids, oldPlid);

			long oldGroupId = GetterUtil.getLong(matcher.group(6));

			long newGroupId = oldGroupId;

			long oldLayoutId = GetterUtil.getLong(matcher.group(1));

			long newLayoutId = oldLayoutId;

			Layout layout = LayoutLocalServiceUtil.fetchLayout(newPlid);

			if (layout != null) {
				newGroupId = layout.getGroupId();
				newLayoutId = layout.getLayoutId();
			}
			else if (_log.isDebugEnabled()) {
				StringBundler sb = new StringBundler(5);

				sb.append("Unable to get layout with plid ");
				sb.append(oldPlid);
				sb.append(", using layout ID  ");
				sb.append(newLayoutId);
				sb.append(" instead");

				_log.debug(sb.toString());
			}

			String oldLinkToLayout = matcher.group(0);

			String newLinkToLayout = StringUtil.replaceFirst(
				oldLinkToLayout,
				new String[] {
					StringPool.AT + oldPlid, String.valueOf(oldLayoutId)
				},
				new String[] {StringPool.BLANK, String.valueOf(newLayoutId)});

			if ((layout != null) && layout.isPublicLayout() &&
				layoutsImportMode.equals(
					PortletDataHandlerKeys.
						LAYOUTS_IMPORT_MODE_CREATED_FROM_PROTOTYPE)) {

				newLinkToLayout = StringUtil.replace(
					newLinkToLayout, "private-group", "public");
			}

			if ((oldGroupId != 0) && (oldGroupId != newGroupId)) {
				newLinkToLayout = StringUtil.replaceLast(
					newLinkToLayout, String.valueOf(oldGroupId),
					String.valueOf(newGroupId));
			}

			oldLinksToLayout.add(oldLinkToLayout);
			newLinksToLayout.add(newLinkToLayout);
		}

		content = StringUtil.replace(
			content, ArrayUtil.toStringArray(oldLinksToLayout.toArray()),
			ArrayUtil.toStringArray(newLinksToLayout.toArray()));

		return content;
	}

	protected void validateDLReferences(long groupId, String content)
		throws PortalException {

		String portalURL = PortalUtil.getPathContext();

		ServiceContext serviceContext =
			ServiceContextThreadLocal.getServiceContext();

		if ((serviceContext != null) &&
			(serviceContext.getThemeDisplay() != null)) {

			ThemeDisplay themeDisplay = serviceContext.getThemeDisplay();

			portalURL =
				PortalUtil.getPortalURL(themeDisplay) +
					PortalUtil.getPathContext();
		}

		String[] patterns = {
			portalURL.concat("/c/document_library/get_file?"),
			portalURL.concat("/documents/"),
			portalURL.concat("/image/image_gallery?")
		};

		String[] completePatterns = new String[patterns.length];

		long[] companyIds = PortalUtil.getCompanyIds();

		for (long companyId : companyIds) {
			Company company = CompanyLocalServiceUtil.getCompany(companyId);

			String webId = company.getWebId();

			int i = 0;

			for (String pattern : patterns) {
				completePatterns[i] = webId.concat(pattern);

				i++;
			}

			int beginPos = -1;
			int endPos = content.length();

			while (true) {
				beginPos = StringUtil.lastIndexOfAny(
					content, completePatterns, endPos);

				if (beginPos == -1) {
					break;
				}

				Map<String, String[]> dlReferenceParameters =
					getDLReferenceParameters(
						groupId, content,
						beginPos + portalURL.length() + webId.length(), endPos);

				FileEntry fileEntry = getFileEntry(dlReferenceParameters);

				if (fileEntry == null) {
					throw new NoSuchFileEntryException();
				}

				endPos = beginPos - 1;
			}
		}
	}

	protected void validateLayoutReferences(long groupId, String content)
		throws PortalException {

		if (!isValidateLayoutReferences()) {
			return;
		}

		Group group = GroupLocalServiceUtil.getGroup(groupId);

		String[] patterns = {"href=", "[["};

		int beginPos = -1;
		int endPos = content.length();
		int offset = 0;

		while (true) {
			if (beginPos > -1) {
				endPos = beginPos - 1;
			}

			beginPos = StringUtil.lastIndexOfAny(content, patterns, endPos);

			if (beginPos == -1) {
				break;
			}

			if (content.startsWith("href=", beginPos)) {
				offset = 5;

				char c = content.charAt(beginPos + offset);

				if ((c == CharPool.APOSTROPHE) || (c == CharPool.QUOTE)) {
					offset++;
				}
			}
			else if (content.charAt(beginPos) == CharPool.OPEN_BRACKET) {
				offset = 2;
			}

			endPos = StringUtil.indexOfAny(
				content, LAYOUT_REFERENCE_STOP_CHARS, beginPos + offset,
				endPos);

			if (endPos == -1) {
				continue;
			}

			String url = content.substring(beginPos + offset, endPos);

			endPos = url.indexOf(Portal.FRIENDLY_URL_SEPARATOR);

			if (endPos != -1) {
				url = url.substring(0, endPos);
			}

			if (url.endsWith(StringPool.SLASH)) {
				url = url.substring(0, url.length() - 1);
			}

			StringBundler urlSB = new StringBundler(1);

			url = replaceExportHostname(groupId, url, urlSB);

			if (!url.startsWith(StringPool.SLASH)) {
				continue;
			}

			String pathContext = PortalUtil.getPathContext();

			if (pathContext.length() > 1) {
				if (!url.startsWith(pathContext)) {
					continue;
				}

				url = url.substring(pathContext.length());
			}

			if (!url.startsWith(StringPool.SLASH)) {
				continue;
			}

			int pos = url.indexOf(StringPool.SLASH, 1);

			String localePath = StringPool.BLANK;

			Locale locale = null;

			if (pos != -1) {
				localePath = url.substring(0, pos);

				locale = LocaleUtil.fromLanguageId(
					localePath.substring(1), true, false);
			}

			if (locale != null) {
				String urlWithoutLocale = url.substring(localePath.length());

				if (urlWithoutLocale.startsWith(
						PRIVATE_GROUP_SERVLET_MAPPING) ||
					urlWithoutLocale.startsWith(PRIVATE_USER_SERVLET_MAPPING) ||
					urlWithoutLocale.startsWith(PUBLIC_GROUP_SERVLET_MAPPING)) {

					url = urlWithoutLocale;
				}
			}

			boolean privateLayout = false;

			if (url.startsWith(PRIVATE_GROUP_SERVLET_MAPPING)) {
				url = url.substring(PRIVATE_GROUP_SERVLET_MAPPING.length() - 1);

				privateLayout = true;
			}
			else if (url.startsWith(PRIVATE_USER_SERVLET_MAPPING)) {
				url = url.substring(PRIVATE_USER_SERVLET_MAPPING.length() - 1);

				privateLayout = true;
			}
			else if (url.startsWith(PUBLIC_GROUP_SERVLET_MAPPING)) {
				url = url.substring(PUBLIC_GROUP_SERVLET_MAPPING.length() - 1);
			}
			else {
				String urlSBString = urlSB.toString();

				LayoutSet layoutSet = null;

				if (urlSBString.contains(
						DATA_HANDLER_PUBLIC_LAYOUT_SET_SECURE_URL) ||
					urlSBString.contains(DATA_HANDLER_PUBLIC_LAYOUT_SET_URL)) {

					layoutSet = group.getPublicLayoutSet();
				}
				else if (urlSBString.contains(
							DATA_HANDLER_PRIVATE_LAYOUT_SET_SECURE_URL) ||
						 urlSBString.contains(
							 DATA_HANDLER_PRIVATE_LAYOUT_SET_URL)) {

					layoutSet = group.getPrivateLayoutSet();
				}

				if (layoutSet == null) {
					continue;
				}

				privateLayout = layoutSet.isPrivateLayout();
			}

			Layout layout = LayoutLocalServiceUtil.fetchLayoutByFriendlyURL(
				groupId, privateLayout, url);

			if (layout != null) {
				continue;
			}

			String siteAdminURL =
				GroupConstants.CONTROL_PANEL_FRIENDLY_URL +
					PropsValues.CONTROL_PANEL_LAYOUT_FRIENDLY_URL;

			if (url.endsWith(
					VirtualLayoutConstants.CANONICAL_URL_SEPARATOR +
						siteAdminURL)) {

				url = url.substring(url.indexOf(siteAdminURL));
			}

			pos = url.indexOf(StringPool.SLASH, 1);

			String groupFriendlyURL = url;

			if (pos != -1) {
				groupFriendlyURL = url.substring(0, pos);
			}

			Group urlGroup = GroupLocalServiceUtil.fetchFriendlyURLGroup(
				group.getCompanyId(), groupFriendlyURL);

			if (urlGroup == null) {
				throw new NoSuchLayoutException();
			}

			if (pos == -1) {
				continue;
			}

			url = url.substring(pos);

			layout = LayoutLocalServiceUtil.getFriendlyURLLayout(
				urlGroup.getGroupId(), privateLayout, url);
		}
	}

	protected void validateLinksToLayoutsReferences(String content)
		throws PortalException {

		Matcher matcher = exportLinksToLayoutPattern.matcher(content);

		while (matcher.find()) {
			long groupId = GetterUtil.getLong(matcher.group(5));

			String type = matcher.group(2);

			boolean privateLayout = type.startsWith("private");

			long layoutId = GetterUtil.getLong(matcher.group(1));

			Layout layout = LayoutLocalServiceUtil.fetchLayout(
				groupId, privateLayout, layoutId);

			if (layout == null) {
				throw new NoSuchLayoutException();
			}
		}
	}

	protected static final String DATA_HANDLER_COMPANY_SECURE_URL =
		"@data_handler_company_secure_url@";

	protected static final String DATA_HANDLER_COMPANY_URL =
		"@data_handler_company_url@";

	protected static final String DATA_HANDLER_GROUP_FRIENDLY_URL =
		"@data_handler_group_friendly_url@";

	protected static final String DATA_HANDLER_PATH_CONTEXT =
		"@data_handler_path_context@";

	protected static final String DATA_HANDLER_PRIVATE_GROUP_SERVLET_MAPPING =
		"@data_handler_private_group_servlet_mapping@";

	protected static final String DATA_HANDLER_PRIVATE_LAYOUT_SET_SECURE_URL =
		"@data_handler_private_layout_set_secure_url@";

	protected static final String DATA_HANDLER_PRIVATE_LAYOUT_SET_URL =
		"@data_handler_private_layout_set_url@";

	protected static final String DATA_HANDLER_PRIVATE_USER_SERVLET_MAPPING =
		"@data_handler_private_user_servlet_mapping@";

	protected static final String DATA_HANDLER_PUBLIC_LAYOUT_SET_SECURE_URL =
		"@data_handler_public_layout_set_secure_url@";

	protected static final String DATA_HANDLER_PUBLIC_LAYOUT_SET_URL =
		"@data_handler_public_layout_set_url@";

	protected static final String DATA_HANDLER_PUBLIC_SERVLET_MAPPING =
		"@data_handler_public_servlet_mapping@";

	protected static final String DATA_HANDLER_SITE_ADMIN_URL =
		"@data_handler_site_admin_url@";

	protected static final char[] DL_REFERENCE_LEGACY_STOP_CHARS = {
		CharPool.APOSTROPHE, CharPool.CLOSE_BRACKET, CharPool.CLOSE_CURLY_BRACE,
		CharPool.CLOSE_PARENTHESIS, CharPool.GREATER_THAN, CharPool.LESS_THAN,
		CharPool.PIPE, CharPool.QUOTE, CharPool.SPACE
	};

	protected static final char[] DL_REFERENCE_STOP_CHARS = {
		CharPool.APOSTROPHE, CharPool.CLOSE_BRACKET, CharPool.CLOSE_CURLY_BRACE,
		CharPool.CLOSE_PARENTHESIS, CharPool.GREATER_THAN, CharPool.LESS_THAN,
		CharPool.PIPE, CharPool.QUESTION, CharPool.QUOTE, CharPool.SPACE
	};

	protected static final char[] LAYOUT_REFERENCE_STOP_CHARS = {
		CharPool.APOSTROPHE, CharPool.CLOSE_BRACKET, CharPool.CLOSE_CURLY_BRACE,
		CharPool.CLOSE_PARENTHESIS, CharPool.GREATER_THAN, CharPool.LESS_THAN,
		CharPool.PIPE, CharPool.POUND, CharPool.QUESTION, CharPool.QUOTE,
		CharPool.SPACE
	};

	protected static final String PRIVATE_GROUP_SERVLET_MAPPING =
		PropsUtil.get(
			PropsKeys.LAYOUT_FRIENDLY_URL_PRIVATE_GROUP_SERVLET_MAPPING) +
				StringPool.SLASH;

	protected static final String PRIVATE_USER_SERVLET_MAPPING =
		PropsUtil.get(
			PropsKeys.LAYOUT_FRIENDLY_URL_PRIVATE_USER_SERVLET_MAPPING) +
				StringPool.SLASH;

	protected static final String PUBLIC_GROUP_SERVLET_MAPPING =
		PropsUtil.get(
			PropsKeys.LAYOUT_FRIENDLY_URL_PUBLIC_SERVLET_MAPPING) +
				StringPool.SLASH;

	protected static final Pattern exportLinksToLayoutPattern = Pattern.compile(
		"\\[([\\d]+)@(private(-group|-user)?|public)(@([\\d]+))?\\]");
	protected static final Pattern importLinksToLayoutPattern = Pattern.compile(
		"\\[([\\d]+)@(private(-group|-user)?|public)@([\\d]+)(@([\\d]+))?\\]");

	private static final Log _log = LogFactoryUtil.getLog(
		BaseTextExportImportContentProcessor.class);

}