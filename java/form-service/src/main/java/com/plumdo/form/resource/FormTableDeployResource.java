package com.plumdo.form.resource;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.plumdo.common.client.jdbc.JdbcClient;
import com.plumdo.common.resource.BaseResource;
import com.plumdo.form.constant.TableConstant;
import com.plumdo.form.domain.ByteArray;
import com.plumdo.form.domain.FormDefinition;
import com.plumdo.form.domain.FormField;
import com.plumdo.form.domain.FormLayout;
import com.plumdo.form.domain.FormTable;
import com.plumdo.form.repository.ByteArrayRepository;
import com.plumdo.form.repository.FormDefinitionRepository;
import com.plumdo.form.repository.FormFieldRepository;
import com.plumdo.form.repository.FormLayoutRepository;
import com.plumdo.form.repository.FormTableRepository;

/**
 * 数据表部署资源类
 *
 * @author wengwh
 * @date 2018年8月29日
 */
@RestController
public class FormTableDeployResource extends BaseResource {
	@Autowired
	private FormTableRepository formTableRepository;
	@Autowired
	private FormFieldRepository formFieldRepository;
	@Autowired
	private FormLayoutRepository formLayoutRepository;
	@Autowired
	private ByteArrayRepository byteArrayRepository;
	@Autowired
	private FormDefinitionRepository formDefinitionRepository;
	@Autowired
	private JdbcClient jdbcClient;

	@PostMapping("/form-tables/{id}/deploy")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deployFormTable(@PathVariable Integer id) {
		FormTable formTable = formTableRepository.findOne(id);
		List<FormField> formFields = formFieldRepository.findByTableId(id);
		List<FormLayout> formLayouts = formLayoutRepository.findByTableId(id);

		FormDefinition formDefinition = formDefinitionRepository.findLatestFormDefinitionByKey(formTable.getKey());
		if (formDefinition == null) {
			formDefinition = new FormDefinition();
			formDefinition.setVersion(1);
			formDefinition.setKey(formTable.getKey());
			formDefinition.setRelationTable(TableConstant.RELATION_TABLE_PRE + formTable.getKey());
			createDataTable(formDefinition.getRelationTable(), formFields);
		} else {
			formDefinition.setVersion(formDefinition.getVersion() + 1);
			alterDataTable(formDefinition.getRelationTable(), formFields);
		}

		formDefinition.setName(formTable.getName());
		formDefinition.setCategory(formTable.getCategory());
		formDefinition.setTenantId(formTable.getTenantId());

		try {
			ByteArray byteArray = new ByteArray();
			byteArray.setName(formTable.getName() + TableConstant.TABLE_FIELD_SUFFIX);
			byteArray.setContentByte(objectMapper.writeValueAsBytes(formFields));
			byteArrayRepository.save(byteArray);
			formDefinition.setFieldSourceId(byteArray.getId());

			byteArray = new ByteArray();
			byteArray.setName(formTable.getName() + TableConstant.TABLE_LAYOUT_SUFFIX);
			byteArray.setContentByte(objectMapper.writeValueAsBytes(formLayouts));
			byteArrayRepository.save(byteArray);
			formDefinition.setLayoutSourceId(byteArray.getId());

			formDefinitionRepository.save(formDefinition);
		} catch (Exception e) {
			logger.error("deploy form exception", e);
		}
	}

	private void createDataTable(String tableName, List<FormField> formFields) {
		StringBuffer sbSql = new StringBuffer().append("CREATE TABLE plumdo_form.").append(tableName)
				.append("( `id_` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID', ");
		for (FormField formField : formFields) {
			sbSql.append("`" + formField.getKey() + "` VARCHAR(512),");
		}
		sbSql.append("PRIMARY KEY ( id_ ))");

		jdbcClient.execute(sbSql.toString());
	}

	private void alterDataTable(String tableName, List<FormField> formFields) {
		String sql = "select column_name from information_schema.columns where table_name='" + tableName + "'";
		List<String> columns = jdbcClient.queryForList(sql).stream().map(map -> map.getAsString("column_name"))
				.collect(Collectors.toList());

		for (FormField formField : formFields) {
			String key = formField.getKey();
			if (!columns.contains(key)) {
				String alterSql = "ALTER TABLE plumdo_form." + tableName + " ADD COLUMN `" + key + "` VARCHAR(512);";
				jdbcClient.execute(alterSql);
			}
		}
	}

}
