(function () {
  if (!Array.prototype.at) {
    Object.defineProperty(Array.prototype, 'at', {
      value: function (index) {
        var i = Number(index) || 0;
        if (i < 0) i += this.length;
        return this[i];
      },
      writable: true,
      configurable: true,
    });
  }
  if (!Object.hasOwn) {
    Object.hasOwn = function (obj, prop) {
      return Object.prototype.hasOwnProperty.call(Object(obj), prop);
    };
  }
})();
