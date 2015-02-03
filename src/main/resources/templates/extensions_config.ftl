<#include "header.ftl">
<#include "status_messages.ftl">
<#include "navigation.ftl">

<h1>${TITLE}</h1>

<form action="${ACTION}" method="post">
<table>
<tr>
  <td>
    ${I18N_ADD_OBR}
  </td><td>
    <input class="ui-widget ui-widget-content ui-corner-all" type="text" name="obr" style="min-width:600px; max-width:600px" />
  </td><td>
    <input class="ui-button" type="submit" name="add_obr" value="${I18N_ADD}" />
  </td>
</tr>
<tr>
  <td valign="top">
    ${I18N_INSTALLED_OBRS}
  </td><td>
    <select class="ui-widget ui-widget-content ui-corner-all" name="obrs" size="20" multiple="multiple" style="min-width:600px; max-width:600px">
        <#list OBRS as obr>
        <option value="${obr}">${obr}</option> 
        </#list>
    </select>
  </td><td valign="top">
    <input class="ui-button" type="submit" name="remove_obrs" value="${I18N_DELETE_SELECTED}" />
  </td>
</tr>
</table>
</form>
<#include "footer.ftl">