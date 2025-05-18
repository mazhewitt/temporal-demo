import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Box, 
  Button, 
  Card, 
  CardContent, 
  CardHeader, 
  FormControl,
  FormHelperText,
  Grid, 
  InputLabel,
  MenuItem,
  Select,
  TextField, 
  Typography 
} from '@mui/material';
import { OrderService } from '../services/OrderService';

const productTypes = [
  'Equity Swap',
  'Fixed Income Swap',
  'Structured Note',
  'Credit Default Swap',
  'Interest Rate Swap',
  'FX Option'
];

const OrderForm = () => {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    productType: '',
    quantity: '',
    client: ''
  });
  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData({
      ...formData,
      [name]: value
    });
    
    // Clear error when field is edited
    if (errors[name]) {
      setErrors({
        ...errors,
        [name]: null
      });
    }
  };

  const validate = () => {
    const newErrors = {};
    
    if (!formData.productType) {
      newErrors.productType = 'Product type is required';
    }
    
    if (!formData.quantity) {
      newErrors.quantity = 'Quantity is required';
    } else if (isNaN(formData.quantity) || parseInt(formData.quantity) <= 0) {
      newErrors.quantity = 'Quantity must be a positive number';
    }
    
    if (!formData.client) {
      newErrors.client = 'Client name is required';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!validate()) return;
    
    setIsSubmitting(true);
    
    try {
      const orderData = {
        ...formData,
        quantity: parseInt(formData.quantity)
      };
      
      const response = await OrderService.submitOrder(orderData);
      
      // Redirect to the order detail page
      navigate(`/orders/${response.orderId}`);
    } catch (error) {
      console.error('Error submitting order:', error);
      setErrors({
        form: 'An error occurred while submitting the order. Please try again.'
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Submit New Order
      </Typography>
      
      <Card>
        <CardHeader title="Order Details" />
        <CardContent>
          <form onSubmit={handleSubmit}>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.productType}>
                  <InputLabel>Product Type</InputLabel>
                  <Select
                    name="productType"
                    value={formData.productType}
                    onChange={handleChange}
                    label="Product Type"
                  >
                    {productTypes.map(type => (
                      <MenuItem key={type} value={type}>
                        {type}
                      </MenuItem>
                    ))}
                  </Select>
                  {errors.productType && (
                    <FormHelperText>{errors.productType}</FormHelperText>
                  )}
                </FormControl>
              </Grid>
              
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Quantity"
                  name="quantity"
                  type="number"
                  value={formData.quantity}
                  onChange={handleChange}
                  error={!!errors.quantity}
                  helperText={errors.quantity}
                />
              </Grid>
              
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Client Name"
                  name="client"
                  value={formData.client}
                  onChange={handleChange}
                  error={!!errors.client}
                  helperText={errors.client}
                />
              </Grid>
              
              {errors.form && (
                <Grid item xs={12}>
                  <Typography color="error">
                    {errors.form}
                  </Typography>
                </Grid>
              )}
              
              <Grid item xs={12}>
                <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                  <Button 
                    variant="contained" 
                    color="primary" 
                    type="submit"
                    disabled={isSubmitting}
                  >
                    {isSubmitting ? 'Submitting...' : 'Submit Order'}
                  </Button>
                </Box>
              </Grid>
            </Grid>
          </form>
        </CardContent>
      </Card>
    </Box>
  );
};

export default OrderForm;
