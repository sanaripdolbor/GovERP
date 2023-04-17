app = angular.module("axelor.app")
app.controller("ModuleChangeCtrl", ($scope) => {
    $scope.$watch("record.menuBuilder.tmpModule", () => {
        if ($scope.record.menuBuilder != null) {
            $scope.record.module = $scope.record.menuBuilder.tmpModule;
        }
    })
    $scope.$watch("record.menuBuilder.parentMenu", () => {
        if ($scope.record.menuBuilder != null) {
            if ($('[name=parentMenu]').scope().getValue() == null) {
                $scope.record.module = 'custom-model';
            }
        }
    })
    $scope.$watch("record.menuBuilder", () => {
        if ($scope.record.menuBuilder == null) {
            $scope.record.module = 'custom-model';
        }
    })
})
