import groovy.sql.Sql
import groovy.text.GStringTemplateEngine
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class GenerateXml extends DefaultTask {

    Sql sql
    def engine = new GStringTemplateEngine()
    def xmlTemplate = engine.createTemplate(new File('buildSrc/src/main/groovy/template/view.template'))
    def template = engine.createTemplate(new File('buildSrc/src/main/groovy/template/model.template'))
    def actionTemplate = engine.createTemplate(new File('buildSrc/src/main/groovy/template/action.template'))
    def menuTemplate = '''<menuitem name="${name}" \
                            <%= title != null ? String.format('title="%s"',title) : '' %> \
                            <%= order_seq != null ? String.format('order="%s"',order_seq) : '' %> \
                            <%= icon != null ? String.format('icon="%s"',icon) : '' %> \
                            <%= icon_background != null ? String.format('icon-background="%s"',icon_background) : '' %> \
                            <%= action != null ? String.format('action="%s"',action) : '' %> \
                            <%= parent != null ? String.format('parent="%s"',parent) : '' %> \
                          />'''
    def modulePath
    def moduleName
    String generatePath

    @TaskAction
    void createPreData() {
        if (modulePath == null || generatePath == null) {
            throw new RuntimeException("xgtool not configured")
        }
        def models = getModels()
        for (def model : models) {
            if (model != null && (model.get("fields") as List).size() < 1) {
                continue
            }
            model.put("module_name", moduleName)
            model.put("module_path", modulePath)
            if (model.get("module") != null && model.get("module") != moduleName) {
                def groovyRowResult = getModule(model.get("module") as String);
                model.put("module_path", groovyRowResult.get("package_name"))
                model.put("module_name", groovyRowResult.get("module_name"))
            }
            def domain = new File(generatePath + "/domains/" + model.get("name").toString() + ".xml")
            domain.write(template.make(model).toString())

            def view = new File(generatePath + "/views/" + model.get("name").toString() + ".xml")

            StringBuilder builder = new StringBuilder()
            builder.append('''<object-views xmlns="http://axelor.com/xml/ns/object-views" \
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                  xsi:schemaLocation="http://axelor.com/xml/ns/object-views http://axelor.com/xml/ns/object-views/object-views_5.4.xsd">
            ''')

            model.put("view_type", "grid")
            def result = xmlTemplate.make(model)
            builder.append(result)

            model.put("view_type", "form")
            result = xmlTemplate.make(model)
            builder.append(result)

            def menuBuilderId = model.get("menu_builder") as Long
            if (menuBuilderId != null) {
                def row =
                        sql.firstRow("select xml_id, meta_menu from studio_menu_builder where id = :id", id: menuBuilderId)
                model.put("xml_id", row.get("xml_id") as String)
                def temp = actionTemplate.make(model)
                sql.execute("update meta_action set xml = :xml where xml_id = :id", xml: temp.toString(), id: row.get("xml_id") as String)
                builder.append(temp).append("\n")
                if (row.get("meta_menu") != null) {
                    def metaMenu = sql.firstRow("select * from meta_menu where id = ?", row.get("meta_menu"))
                    if (metaMenu.get("action") != null) {
                        def menuAction = sql.firstRow("select * from meta_action where id = ?", metaMenu.get("action") as Long)
                        metaMenu.put("action", menuAction.get("name"))
                    }
                    if (metaMenu.get("parent") != null) {
                        def menuAction = sql.firstRow("select * from meta_menu where id = ?", metaMenu.get("parent") as Long)
                        metaMenu.put("parent", menuAction.get("name"))
                    }
                    builder.append(engine.createTemplate(menuTemplate).make(metaMenu)).append("\n")
                }
            }

            builder.append('''</object-views>''')
            if (view.exists()) {
                view.write(builder.toString())
            } else {
                view.createNewFile()
                view.append(builder.toString())
            }


        }

        sql.close()
    }

    def getModule(String name) {
        return sql.firstRow("select mm.package_name as package_name,lower(split_part(m.name, '-', 2)) || lower(split_part(m.name, '-', 3)) as module_name from meta_model mm inner join meta_module m on lower(split_part(m.name, '-', 2)) || lower(split_part(m.name, '-', 3)) like lower(split_part(mm.table_name, '_', 1)) where m.name = ?", name)
    }

    void updateXml(Long id, String xml) {
        sql.execute('update meta_view set xml = :xml where id = :id', xml: xml, id: id)
    }

    List getModels() {
        return sql.rows('select id, name, title, form_view, grid_view, menu, menu_builder, module from meta_json_model where is_completed is true')
                .collect {
                    def fields = getFields(it.get("id") as Long)
                    fields.findAll {
                        def jsonModelId = it.get("target_json_model")
                        if (jsonModelId == null) return
                        def row = sql.firstRow("select * from meta_json_model where id = ?;", jsonModelId as Long)
                        it.put("target_model", row.get("name"))
                        if (row.get("module") != null && getModule(row.get("module") as String) != null) {
                            it.put("module_path", getModule(row.get("module") as String).get("package_name"))
                        } else {
                            it.put("module_path", modulePath as String)
                        }
                    }
                    fields.sort { it.get("column_sequence") as Integer }
                    it.put("fields", fields)
                    it
                }
    }

    List getFields(Long jsonModelId) {
        return sql.rows("""
            select 
                *,
                split_part(unique_model, ' ', 2) as model_name_from_json
            from meta_json_field
            where 
            json_model = $jsonModelId
                and
            lower(type_name) not in ('panel', 'spacer', 'button', 'label', 'separator')""")
    }

}
