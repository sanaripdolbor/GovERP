app = angular.module("axelor.app")
app.controller("MetaJsonFieldController", ($scope) => {

    $scope.$watch("record.isSearchField", (value) => {
        if (!value) {
            $scope.record.searchFields = undefined
            $scope.record.templateField = undefined
            $scope.record.checkBoxField = undefined
            return
        }
    })

    $scope.$watch("record.templateField", () => {
        let text = $scope.record.templateField
        if (undefined === text) {
            $scope.array = []
            return
        }

        if (text === '') {
            $scope.setFieldValue('')
            $scope.array = []
            return
        }

        $scope.array = text
    })

    $scope.checkField = (item) => {
        let searchFields = $scope.record.searchFields
        searchFields = searchFields + ''
        if (searchFields !== undefined && searchFields.includes(item))
            $scope.unsetFieldValue(item)
        else $scope.setFieldValue(item)
    }

    $scope.setFieldValue = (text) => {
        let searchFields = $scope.record.searchFields
        if (searchFields !== undefined && searchFields !== '')
            searchFields += ',' + text
        else searchFields = text
        $scope.record.searchFields = searchFields
    }

    $scope.unsetFieldValue = (text) => {
        let searchFields = $scope.record.searchFields + ''
        if (searchFields.startsWith(text))
            if (searchFields.startsWith(text + ','))
                searchFields = searchFields.replaceAll(text + ',', '')
            else
                searchFields = searchFields.replaceAll(text, '')
        else
            searchFields = searchFields.replaceAll(',' + text, '')
        $scope.record.searchFields = searchFields

    }
})